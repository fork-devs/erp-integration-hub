package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessWebhooksUseCase;
import com.erp.integrationhub.domain.model.ProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/webhooks")
@Tag(name = "Webhook Receivers", description = "Endpoints for receiving signed webhooks from external providers (Stripe, Nubefact)")
public class WebhookReceiverController {

    private final ProcessWebhooksUseCase processWebhooksUseCase;

    public WebhookReceiverController(ProcessWebhooksUseCase processWebhooksUseCase) {
        this.processWebhooksUseCase = processWebhooksUseCase;
    }

    @PostMapping("/stripe")
    @Operation(summary = "Receive Stripe webhook events", description = "Verifies HMAC-SHA256 signature and processes asynchronous payment notifications")
    public ResponseEntity<Map<String, Object>> receiveStripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {

        boolean processed = processWebhooksUseCase.processWebhook(ProviderType.STRIPE, payload, signature);
        return ResponseEntity.ok(Map.of(
                "received", true,
                "processed", processed
        ));
    }

    @PostMapping("/nubefact")
    @Operation(summary = "Receive Nubefact CDR / SUNAT status webhook", description = "Verifies webhook signature and updates invoice status")
    public ResponseEntity<Map<String, Object>> receiveNubefactWebhook(
            @RequestHeader(value = "X-Nubefact-Signature", required = false) String signature,
            @RequestBody String payload) {

        boolean processed = processWebhooksUseCase.processWebhook(ProviderType.NUBEFACT, payload, signature);
        return ResponseEntity.ok(Map.of(
                "received", true,
                "processed", processed
        ));
    }
}
