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
    private final String apisunatWebhookSecret;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final ObjectMapper objectMapper;

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort) {
        this.stripeWebhookSecret = stripeWebhookSecret != null ? stripeWebhookSecret.trim() : "";
        this.apisunatWebhookSecret = apisunatWebhookSecret != null ? apisunatWebhookSecret.trim() : "";
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort) {
        this(stripeWebhookSecret, apisunatWebhookSecret, attemptRepositoryPort, null);
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
            validSignature = verifyStandardHmacSignature(payload, signatureHeader, apisunatWebhookSecret);
        }

        if (!validSignature) {
            log.warn("Firma HMAC criptográfica inválida para el proveedor {}", provider);
            throw new InvalidSignatureException("La firma criptográfica del webhook es inválida o expiró");
        }

        log.info("Procesando webhook verificado criptográficamente de {}: {}", provider, payload);

        // 2. Procesar lógica de negocio y actualizar estados
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
                        idempotencyRepositoryPort.save(IdempotencyRecord.of(
                                effectiveTenantId,
                                IdempotencyScope.PAYMENT,
                                referenceId.toString(),
                                objectMapper.writeValueAsString(updatedPago)
                        ));
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
                    idempotencyRepositoryPort.save(IdempotencyRecord.of(
                            effectiveTenantId,
                            IdempotencyScope.VOUCHER,
                            referenceId.toString(),
                            objectMapper.writeValueAsString(updatedResult)
                    ));
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
