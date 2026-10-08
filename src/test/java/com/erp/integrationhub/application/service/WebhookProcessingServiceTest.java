package com.erp.integrationhub.application.service;

import com.erp.integrationhub.domain.exception.InvalidSignatureException;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceTest {

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    private WebhookProcessingService webhookProcessingService;

    private final String secretKey = "production_secret_key_123456";

    @BeforeEach
    void setUp() {
        webhookProcessingService = new WebhookProcessingService(
                secretKey,
                secretKey,
                attemptRepositoryPort
        );
    }

    @Test
    @DisplayName("Should verify valid HMAC-SHA256 signature and process webhook")
    void processWebhook_WithValidSignature_ShouldSucceed() throws Exception {
        String payload = "{\"event\":\"payment_intent.succeeded\",\"id\":\"pi_123\"}";

        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(key);
        String validSignature = HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        boolean result = webhookProcessingService.processWebhook(ProviderType.STRIPE, payload, validSignature);

        assertTrue(result);
        verify(attemptRepositoryPort).save(any());
    }

    @Test
    @DisplayName("Should throw InvalidSignatureException when HMAC signature does not match")
    void processWebhook_WithInvalidSignature_ShouldThrowException() {
        String payload = "{\"event\":\"payment_intent.succeeded\"}";
        String invalidSignature = "deadbeef1234567890abcdef";

        assertThrows(InvalidSignatureException.class, () ->
                webhookProcessingService.processWebhook(ProviderType.STRIPE, payload, invalidSignature)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when payload is null or blank")
    void processWebhook_WithBlankPayload_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                webhookProcessingService.processWebhook(ProviderType.STRIPE, "   ", "sig")
        );
    }
}
