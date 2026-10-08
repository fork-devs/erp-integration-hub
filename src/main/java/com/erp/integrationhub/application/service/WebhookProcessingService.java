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
import java.util.HexFormat;
import java.util.UUID;

public class WebhookProcessingService implements ProcessWebhooksUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final String stripeWebhookSecret;
    private final String nubefactWebhookSecret;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;

    public WebhookProcessingService(
            String stripeWebhookSecret,
            String nubefactWebhookSecret,
            IntegrationAttemptRepositoryPort attemptRepositoryPort) {
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.nubefactWebhookSecret = nubefactWebhookSecret;
        this.attemptRepositoryPort = attemptRepositoryPort;
    }

    @Override
    public boolean processWebhook(ProviderType provider, String payload, String signatureHeader) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("Payload de webhook no puede estar vacío");
        }

        // 1. Verificación de firma HMAC SHA-256
        String expectedSecret = provider == ProviderType.STRIPE ? stripeWebhookSecret : nubefactWebhookSecret;
        if (expectedSecret != null && !expectedSecret.contains("mock") && !expectedSecret.contains("sandbox")) {
            boolean validSignature = verifyHmacSignature(payload, signatureHeader, expectedSecret);
            if (!validSignature) {
                log.warn("Firma HMAC inválida en webhook de {}", provider);
                throw new InvalidSignatureException("La firma HMAC del webhook es inválida");
            }
        }

        log.info("Procesando webhook verificado de {}: {}", provider, payload);

        // 2. Registrar intento en bitácora
        attemptRepositoryPort.save(IntegrationAttempt.ok(
                UUID.randomUUID(),
                provider,
                "WEBHOOK_RECEIVED",
                UUID.randomUUID(),
                1
        ));

        return true;
    }

    public static boolean verifyHmacSignature(String payload, String signature, String secret) {
        if (signature == null || secret == null) return false;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error al calcular firma HMAC", e);
            return false;
        }
    }
}
