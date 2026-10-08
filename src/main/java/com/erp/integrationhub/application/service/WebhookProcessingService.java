package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.usecase.ProcessWebhooksUseCase;
import com.erp.integrationhub.domain.exception.InvalidSignatureException;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
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

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String apisunatWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort) {
        this.stripeWebhookSecret = stripeWebhookSecret != null ? stripeWebhookSecret.trim() : "";
        this.apisunatWebhookSecret = apisunatWebhookSecret != null ? apisunatWebhookSecret.trim() : "";
        this.attemptRepositoryPort = attemptRepositoryPort;
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

        // 2. Registrar intento exitoso en bitácora
        attemptRepositoryPort.save(IntegrationAttempt.ok(
                UUID.randomUUID(),
                provider,
                "WEBHOOK_RECEIVED",
                UUID.randomUUID(),
                1
        ));

        return true;
    }

    /**
     * Verifica la firma oficial de Stripe según el estándar de Stripe:
     * Header format: t=1614555555,v1=5257a869e7ecebeda32affa62cd49f7f9d5ec61852d91173e40e7412d6070573
     * Firma esperada = HMAC_SHA256(secret, "${timestamp}.${payload}")
     */
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

            // Validar tolerancia temporal (protección contra ataques de replay)
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

    /**
     * Verifica una firma estándar HMAC SHA-256 (payload crudo)
     */
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
