package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.result.InvoiceResult;
import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.erp.integrationhub.application.usecase.ProcessWebhooksUseCase;
import com.erp.integrationhub.domain.exception.InvalidSignatureException;
import com.erp.integrationhub.domain.exception.TenantContextMissingException;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
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

/**
 * Application service for cryptographically verifying and processing incoming provider webhooks.
 */
public class WebhookProcessingService implements ProcessWebhooksUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);
    private static final long STRIPE_TIMESTAMP_TOLERANCE_SECONDS = 300; // 5 minutes tolerance

    private final String stripeWebhookSecret;
    private final long timeWindowSeconds;
    private final String apisunatWebhookSecret;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;
    private final ProcessedWebhookRepositoryPort processedWebhookRepositoryPort;
    private final String webhookSigningSecret;

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            ProcessedWebhookRepositoryPort processedWebhookRepositoryPort,
            String webhookSigningSecret,
            long timeWindowSeconds,
            UnitOfWork unitOfWork) {
        this.stripeWebhookSecret = stripeWebhookSecret != null ? stripeWebhookSecret.trim() : "";
        this.apisunatWebhookSecret = apisunatWebhookSecret != null ? apisunatWebhookSecret.trim() : "";
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.processedWebhookRepositoryPort = processedWebhookRepositoryPort;
        this.webhookSigningSecret = webhookSigningSecret != null ? webhookSigningSecret.trim() : "";
        this.timeWindowSeconds = timeWindowSeconds;
        this.unitOfWork = unitOfWork;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this(stripeWebhookSecret, apisunatWebhookSecret, attemptRepositoryPort, null, null, "", 300, unitOfWork);
    }

    /**
     * Checks that the incoming webhook timestamp falls within the allowed sliding window.
     */
    private boolean isWithinTimeWindow(String payload, ProviderType provider) {
        Long timestamp = extractTimestamp(payload, provider);
        if (timestamp == null) {
            return true;
        }
        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - timestamp / 1000);
        return ageSeconds <= timeWindowSeconds;
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
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveWebhookSigningSecret() {
        return !webhookSigningSecret.isBlank() ? webhookSigningSecret : apisunatWebhookSecret;
    }

    private UUID resolveEventId(ProviderType provider, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String id = root.path("id").asText(root.path("event_id").asText(null));
            if (id == null || id.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return UUID.nameUUIDFromBytes(
                        (provider.name() + ":" + id).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String buildOperationKey(UUID tenantId, UUID referenceId) {
        return tenantId != null && referenceId != null
                ? tenantId + ":" + referenceId
                : String.valueOf(referenceId);
    }

    @Override
    public boolean processWebhook(ProviderType provider, String payload, String signatureHeader) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook payload cannot be empty");
        }

        if (signatureHeader == null || signatureHeader.trim().isEmpty()) {
            log.warn("Missing signature header in webhook from {}", provider);
            throw new InvalidSignatureException("Signature header is required");
        }

        // 1. Strict cryptographic verification (Fail-closed)
        boolean validSignature;
        if (provider == ProviderType.STRIPE) {
            validSignature = verifyStripeSignature(payload, signatureHeader, stripeWebhookSecret);
        } else {
            validSignature = verifyStandardHmacSignature(payload, signatureHeader, resolveWebhookSigningSecret());
        }

        if (!validSignature) {
            log.warn("Invalid cryptographic HMAC signature for provider {}", provider);
            throw new InvalidSignatureException("Cryptographic signature is invalid or expired");
        }

        // 1 bis. Enforce replay window tolerance
        if (!isWithinTimeWindow(payload, provider)) {
            log.warn("Webhook from {} outside allowed time window: rejected", provider);
            throw new InvalidSignatureException("Webhook timestamp is outside the allowed window");
        }

        log.info("Processing cryptographically verified webhook from {}: {}", provider, payload);

        // 2. Register processed event to prevent duplicate replays
        UUID eventId = resolveEventId(provider, payload);
        if (eventId != null && processedWebhookRepositoryPort != null) {
            boolean alreadyProcessed = unitOfWork.callInTransaction(
                    () -> processedWebhookRepositoryPort.register(eventId, provider));
            if (alreadyProcessed) {
                log.info("Webhook event {} was already processed: ignoring replay", eventId);
                return true;
            }
        }

        // 3. Process business payload and update corresponding states
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
                String paymentStr = metadata.path("pago_id").asText(metadata.path("pagoId").asText(null));

                if (tenantStr != null) {
                    try { effectiveTenantId = UUID.fromString(tenantStr); } catch (Exception ignored) {}
                }
                if (paymentStr != null) {
                    try { referenceId = UUID.fromString(paymentStr); } catch (Exception ignored) {}
                } else if (stripeId != null) {
                    referenceId = UUID.nameUUIDFromBytes(stripeId.getBytes(StandardCharsets.UTF_8));
                }

                if ("payment_intent.succeeded".equalsIgnoreCase(eventType) || "charge.succeeded".equalsIgnoreCase(eventType)) {
                    if (idempotencyRepositoryPort != null && referenceId != null) {
                        PaymentResult updatedPayment = new PaymentResult(
                                referenceId,
                                true,
                                "PAID",
                                stripeId,
                                null,
                                "AUTH-STRIPE-WEBHOOK",
                                null,
                                false
                        );
                        final String paymentPayload = objectMapper.writeValueAsString(updatedPayment);
                        final UUID targetTenant = effectiveTenantId;
                        final UUID targetRef = referenceId;
                        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                                targetTenant,
                                IdempotencyScope.PAYMENT,
                                buildOperationKey(targetTenant, targetRef),
                                paymentPayload
                        )));
                    }
                }
            } else {
                // APISUNAT Webhook
                JsonNode payloadNode = rootNode.path("payload");
                String status = payloadNode.path("estado").asText(rootNode.path("estado").asText("ACEPTADO"));
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
                    InvoiceResult updatedResult = new InvoiceResult(
                            referenceId,
                            "ACEPTADO".equalsIgnoreCase(status),
                            status,
                            "0",
                            "Voucher updated via SUNAT webhook",
                            pdf,
                            xml,
                            cdr,
                            hash,
                            false
                    );
                    final String invoicePayload = objectMapper.writeValueAsString(updatedResult);
                    final UUID targetTenant = effectiveTenantId;
                    final UUID targetRef = referenceId;
                    unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                            targetTenant,
                            IdempotencyScope.VOUCHER,
                            targetRef.toString(),
                            invoicePayload
                    )));
                }
            }
        } catch (Exception e) {
            log.warn("Error processing structured webhook content: {}", e.getMessage());
        }

        if (effectiveTenantId == null) {
            throw new TenantContextMissingException(
                    "Webhook does not contain target tenant ID and cannot be processed");
        }

        if (referenceId == null) {
            referenceId = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        }

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
                log.warn("Malformed Stripe-Signature header: {}", signatureHeader);
                return false;
            }

            long currentTimestamp = Instant.now().getEpochSecond();
            if (Math.abs(currentTimestamp - timestamp) > STRIPE_TIMESTAMP_TOLERANCE_SECONDS) {
                log.warn("Stripe webhook timestamp out of tolerance window (delta: {}s)", (currentTimestamp - timestamp));
                return false;
            }

            String signedPayload = timestamp + "." + payload;
            String computedSignature = computeHmacSha256(signedPayload, secret);

            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedV1.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying Stripe signature: {}", e.getMessage());
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
            log.error("Error verifying standard HMAC signature: {}", e.getMessage());
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
