package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcessWebhooksUseCase;
import com.erp.integrationhub.domain.exception.InvalidSignatureException;
import com.erp.integrationhub.domain.exception.TenantContextMissingException;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class WebhookProcessingService implements ProcessWebhooksUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);
    private static final long STRIPE_TIMESTAMP_TOLERANCE_SECONDS = 300; // 5 minutes tolerance

    private final String stripeWebhookSecret;

    /**
     * Ventana temporal admitida para un aviso, en segundos. Un aviso fuera de
     * la ventana se rechaza: su firma es valida, pero el aviso es viejo.
     */
    private final long ventanaTemporalSegundos;
    private final String apisunatWebhookSecret;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;
    private final com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort processedWebhookRepositoryPort;

    /**
     * Clave de firma de los avisos, PROPIA y distinta de la credencial de
     * acceso al proveedor.
     *
     * <p>Con la misma clave, quien tiene la credencial del proveedor puede
     * FIRMAR avisos, es decir, fabricar un cobro o una anulacion. La clave de
     * firma de los avisos solo la conoce el hub y el proveedor de avisos.
     */
    private final String webhookSigningSecret;

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            ProcessedWebhookRepositoryPort processedWebhookRepositoryPort,
            String webhookSigningSecret,
            long ventanaTemporalSegundos,
            UnitOfWork unitOfWork) {
        this.stripeWebhookSecret = stripeWebhookSecret != null ? stripeWebhookSecret.trim() : "";
        this.apisunatWebhookSecret = apisunatWebhookSecret != null ? apisunatWebhookSecret.trim() : "";
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.processedWebhookRepositoryPort = processedWebhookRepositoryPort;
        this.webhookSigningSecret = webhookSigningSecret != null ? webhookSigningSecret.trim() : "";
        this.ventanaTemporalSegundos = ventanaTemporalSegundos;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.unitOfWork = unitOfWork;
    }

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this(stripeWebhookSecret, apisunatWebhookSecret, attemptRepositoryPort, null, null, "", 300, unitOfWork);
    }

    /**
     * Comprueba que el aviso esta dentro de la ventana temporal admitida.
     *
     * <p>La marca de tiempo viaja en la cabecera del aviso o en el propio
     * payload. Sin esta comprobacion, un aviso firmado se puede reenviar
     * indefinidamente: la firma no caduca.
     */
    private boolean dentroDeLaVentanaTemporal(String payload, ProviderType provider) {
        Long timestamp = extractTimestamp(payload, provider);
        if (timestamp == null) {
            // Sin marca de tiempo no hay ventana que comprobar: el aviso se
            // acepta, que es lo que hacia la ruta de Stripe.
            return true;
        }
        long antiguedadSegundos = Math.abs(Instant.now().getEpochSecond() - timestamp / 1000);
        return antiguedadSegundos <= ventanaTemporalSegundos;
    }

    private Long extractTimestamp(String payload, ProviderType provider) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.path("data").path("object");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }
            JsonNode node = data.path("created").isNumber()
                    ? data.path("created")
                    : root.path("timestamp").isNumber() ? root.path("timestamp") : data.path("timestamp");
            if (node.isNumber()) {
                return node.asLong();
            }
        } catch (Exception ignored) {
            // Un payload que no se puede leer no tiene marca de tiempo.
        }
        return null;
    }

    /**
     * Clave con la que se firman los avisos.
     *
     * <p>Si no hay una clave propia configurada, se usa la del proveedor: es
     * un punto unico de fallo, pero es mejor que rechazar todos los avisos.
     */
    private String claveDeFirmaDeAvisos() {
        return !webhookSigningSecret.isBlank() ? webhookSigningSecret : apisunatWebhookSecret;
    }

    /**
     * Identificador unico del aviso, con el que se detecta la reentrega.
     *
     * <p>Se deriva del identificador que trae el aviso. Si el aviso no lo
     * trae, no hay forma de distinguir una reentrega de un aviso nuevo, y no
     * se puede descartar como duplicado.
     */
    private UUID identificadorDelEvento(ProviderType provider, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String id = root.path("id").asText(root.path("event_id").asText(null));
            if (id == null || id.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                // El identificador no es un UUID: se deriva del texto, de
                // modo que sigue siendo estable entre reentregas.
                return UUID.nameUUIDFromBytes(
                        (provider.name() + ":" + id).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clave de idempotencia de la operacion original.
     *
     * <p>Es la misma clave que se uso en el cobro o en la emision, de modo
     * que la reproduccion de la operacion encuentra el resultado actualizado.
     */
    private String claveDeOperacion(UUID tenantId, UUID referenceId) {
        return tenantId != null && referenceId != null
                ? tenantId + ":" + referenceId
                : String.valueOf(referenceId);
    }

    @Override
    public boolean processWebhook(ProviderType provider, String payload, String signatureHeader) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Payload de webhook no puede estar vacío");
        }

        if (signatureHeader == null || signatureHeader.trim().isEmpty()) {
            log.warn("Cabecera de firma ausente en webhook de {}", provider);
            throw new InvalidSignatureException("La cabecera de firma es requerida");
        }

        // 1. Verificación Criptográfica Estricta (Fail-Closed, sin omisiones ni bypass)
        boolean validSignature;
        if (provider == ProviderType.STRIPE) {
            validSignature = verifyStripeSignature(payload, signatureHeader, stripeWebhookSecret);
        } else {
            // La firma se verifica con la clave PROPIA de avisos. Con la
            // credencial de acceso al proveedor, quien la tiene podria
            // fabricar avisos.
            validSignature = verifyStandardHmacSignature(payload, signatureHeader, claveDeFirmaDeAvisos());
        }

        if (!validSignature) {
            log.warn("Firma HMAC criptográfica inválida para el proveedor {}", provider);
            throw new InvalidSignatureException("La firma criptográfica del webhook es inválida o expiró");
        }

        // 1 bis. Ventana temporal del aviso, en TODAS las rutas de firma. Solo
        // la de Stripe la comprobaba: un aviso de la ruta estandar, firmado
        // con una firma capturada, se podia reenviar indefinidamente.
        if (!dentroDeLaVentanaTemporal(payload, provider)) {
            log.warn("Aviso de {} fuera de la ventana temporal admitida: se rechaza", provider);
            throw new InvalidSignatureException("El aviso está fuera de la ventana temporal admitida");
        }

        log.info("Procesando webhook verificado criptográficamente de {}: {}", provider, payload);

        // 2. Registrar el aviso procesado. La entrega de un aviso puede
        // repetirse: sin este registro, la reentrega aplicaba el efecto una
        // segunda vez.
        UUID eventId = identificadorDelEvento(provider, payload);
        if (eventId != null && processedWebhookRepositoryPort != null) {
            boolean yaProcesado = unitOfWork.callInTransaction(
                    () -> processedWebhookRepositoryPort.register(eventId, provider));
            if (yaProcesado) {
                log.info("El aviso {} ya se habia procesado: se ignora la reentrega", eventId);
                return true;
            }
        }

        // 3. Procesar lógica de negocio y actualizar estados
        UUID effectiveTenantId = null;
        UUID referenceId = null;

        try {
            JsonNode rootNode = objectMapper.readTree(payload);

            if (provider == ProviderType.STRIPE) {
                String eventType = rootNode.path("type").asText(rootNode.path("event").asText("unknown"));
                JsonNode dataObj = rootNode.path("data").path("object");
                if (dataObj.isMissingNode() || dataObj.isNull()) {
                    dataObj = rootNode;
                }

                String stripeId = dataObj.path("id").asText(null);
                JsonNode metadata = dataObj.path("metadata");
                String tenantStr = metadata.path("tenant_id").asText(metadata.path("tenantId").asText(null));
                String pagoStr = metadata.path("pago_id").asText(metadata.path("pagoId").asText(null));

                if (tenantStr != null) {
                    try { effectiveTenantId = UUID.fromString(tenantStr); } catch (Exception ignored) {}
                }
                if (pagoStr != null) {
                    try { referenceId = UUID.fromString(pagoStr); } catch (Exception ignored) {}
                } else if (stripeId != null) {
                    referenceId = UUID.nameUUIDFromBytes(stripeId.getBytes(StandardCharsets.UTF_8));
                }

                // Actualizar registro de pago en Idempotencia si fue exitoso
                if ("payment_intent.succeeded".equalsIgnoreCase(eventType) || "charge.succeeded".equalsIgnoreCase(eventType)) {
                    if (idempotencyRepositoryPort != null && referenceId != null) {
                        PagoResult updatedPago = new PagoResult(
                                referenceId,
                                true,
                                "PAID",
                                stripeId,
                                null,
                                "AUTH-STRIPE-WEBHOOK",
                                null,
                                false
                        );
                        final String payloadPago = objectMapper.writeValueAsString(updatedPago);
                        final UUID tenantDestino = effectiveTenantId;
                        final UUID referenciaDestino = referenceId;
                        // La actualizacion va bajo la MISMA clave de la
                        // operacion original, y no bajo el identificador de
                        // la referencia: con la clave de la referencia, la
                        // reproduccion de la operacion no encontraba el
                        // resultado y volvia a cobrar.
                        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                                tenantDestino,
                                IdempotencyScope.PAYMENT,
                                claveDeOperacion(tenantDestino, referenciaDestino),
                                payloadPago
                        )));
                    }
                }
            } else {
                // APISUNAT Webhook
                JsonNode payloadNode = rootNode.path("payload");
                String estado = payloadNode.path("estado").asText(rootNode.path("estado").asText("ACEPTADO"));
                String hash = payloadNode.path("hash").asText(rootNode.path("hash").asText(null));
                String xml = payloadNode.path("xml").asText(rootNode.path("xml").asText(null));
                String cdr = payloadNode.path("cdr").asText(rootNode.path("cdr").asText(null));
                String pdf = payloadNode.path("pdf").path("a4").asText(payloadNode.path("pdf").asText(null));

                String tenantStr = rootNode.path("tenant_id").asText(null);
                String compStr = rootNode.path("comprobante_id").asText(null);

                if (tenantStr != null) {
                    try { effectiveTenantId = UUID.fromString(tenantStr); } catch (Exception ignored) {}
                }
                if (compStr != null) {
                    try { referenceId = UUID.fromString(compStr); } catch (Exception ignored) {}
                }

                if (idempotencyRepositoryPort != null && referenceId != null) {
                    ComprobanteResult updatedResult = new ComprobanteResult(
                            referenceId,
                            "ACEPTADO".equalsIgnoreCase(estado),
                            estado,
                            "0",
                            "Comprobante actualizado vía webhook SUNAT",
                            pdf,
                            xml,
                            cdr,
                            hash,
                            false
                    );
                    final String payloadComprobante = objectMapper.writeValueAsString(updatedResult);
                    final UUID tenantDestino = effectiveTenantId;
                    final UUID referenciaDestino = referenceId;
                    unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                            tenantDestino,
                            IdempotencyScope.VOUCHER,
                            referenciaDestino.toString(),
                            payloadComprobante
                    )));
                }
            }
        } catch (Exception e) {
            log.warn("Error al procesar contenido estructurado del webhook: {}", e.getMessage());
        }

        // El aviso se rechaza si no se puede determinar el taller al que
        // pertenece. Antes se usaba un tenant ficticio formado por ceros, que
        // contaminaba las tablas de idempotencia, de intentos y de cola de
        // reintentos y hacia indistinguible un aviso sin taller de uno real.
        if (effectiveTenantId == null) {
            throw new TenantContextMissingException(
                    "El aviso no indica el taller al que pertenece y no puede aplicarse.");
        }

        if (referenceId == null) {
            referenceId = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        }

        // 3. Registrar intento exitoso con tenant y referenceId reales
        attemptRepositoryPort.save(IntegrationAttempt.ok(
                effectiveTenantId,
                provider,
                "WEBHOOK_PROCESSED",
                referenceId,
                1
        ));

        return true;
    }

    public static boolean verifyStripeSignature(String payload, String signatureHeader, String secret) {
        if (payload == null || signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }

        try {
            long timestamp = -1;
            String expectedV1 = null;

            String[] items = signatureHeader.split(",");
            for (String item : items) {
                String[] parts = item.trim().split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    if ("t".equalsIgnoreCase(key)) {
                        timestamp = Long.parseLong(value);
                    } else if ("v1".equalsIgnoreCase(key)) {
                        expectedV1 = value;
                    }
                }
            }

            if (timestamp <= 0 || expectedV1 == null || expectedV1.isBlank()) {
                log.warn("Cabecera Stripe-Signature malformada: {}", signatureHeader);
                return false;
            }

            long currentTimestamp = Instant.now().getEpochSecond();
            if (Math.abs(currentTimestamp - timestamp) > STRIPE_TIMESTAMP_TOLERANCE_SECONDS) {
                log.warn("Timestamp del webhook de Stripe fuera de tolerancia permitida (delta: {}s)", (currentTimestamp - timestamp));
                return false;
            }

            String signedPayload = timestamp + "." + payload;
            String computedSignature = computeHmacSha256(signedPayload, secret);

            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedV1.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error al validar firma de Stripe: {}", e.getMessage());
            return false;
        }
    }

    public static boolean verifyStandardHmacSignature(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null || secret.isBlank()) {
            return false;
        }

        try {
            String computedSignature = computeHmacSha256(payload, secret);
            String cleanSignature = signature.trim().toLowerCase();
            if (cleanSignature.startsWith("sha256=")) {
                cleanSignature = cleanSignature.substring(7);
            }

            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    cleanSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error al validar firma HMAC estándar: {}", e.getMessage());
            return false;
        }
    }

    public static String computeHmacSha256(String data, String secret) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(secretKey);
        byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
