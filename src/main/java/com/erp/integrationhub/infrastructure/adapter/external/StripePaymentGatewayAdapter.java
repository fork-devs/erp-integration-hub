package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.ChargeRequest;
import com.erp.integrationhub.domain.model.Currency;
import com.erp.integrationhub.domain.model.PaymentOutcome;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * External adapter communicating with Stripe PaymentIntents API for credit/debit card processing.
 */
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    private final String apiKey;
    private final boolean sandboxMode;

    public StripePaymentGatewayAdapter(String apiKey, boolean sandboxMode) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.sandboxMode = sandboxMode;
        if (!this.apiKey.isEmpty() && !this.apiKey.contains("mock")) {
            Stripe.apiKey = this.apiKey;
        }
    }

    @Override
    public PaymentOutcome processCharge(ChargeRequest request) {
        log.info("[STRIPE ACL] Processing charge of {} {} with idempotencyKey={}",
                request.getCurrency(), request.getAmount(), request.getIdempotencyKey());

        try {
            long amountInCents = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
            String currency = request.getCurrency() == Currency.USD ? "usd" : "pen";

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription(request.getDescription())
                    .setPaymentMethod(request.getCardToken())
                    .setConfirm(true)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    );

            if (request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()) {
                paramsBuilder.setReceiptEmail(request.getCustomerEmail());
            }

            paramsBuilder.putMetadata("tenant_id", request.getTenantId().toString());
            paramsBuilder.putMetadata("payment_id", request.getPaymentId().toString());

            RequestOptions options = RequestOptions.builder()
                    .setApiKey(apiKey)
                    .setIdempotencyKey(request.getIdempotencyKey())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), options);

            log.info("[STRIPE ACL] PaymentIntent created with status: {} (ID: {})", paymentIntent.getStatus(), paymentIntent.getId());

            return switch (paymentIntent.getStatus()) {
                case "succeeded" -> PaymentOutcome.success(paymentIntent.getId(), paymentIntent.getId());
                case "requires_action", "requires_confirmation" -> PaymentOutcome.requiresAction(paymentIntent.getId(), paymentIntent.getClientSecret());
                case "processing" -> new PaymentOutcome(true, "PENDING", paymentIntent.getId(), paymentIntent.getClientSecret(), null, null);
                default -> PaymentOutcome.failure("Unsuccessful status from Stripe: " + paymentIntent.getStatus());
            };

        } catch (CardException e) {
            log.warn("[STRIPE ACL] Card declined: {} (Code: {})", e.getMessage(), e.getCode());
            return PaymentOutcome.failure("Card declined: " + e.getUserMessage());
        } catch (StripeException e) {
            log.error("[STRIPE ACL] Communication error with Stripe API: {}", e.getMessage());
            throw new ProviderUnavailableException("Error communicating with Stripe: " + e.getMessage());
        } catch (Exception e) {
            log.error("[STRIPE ACL] Internal error processing payment in Stripe", e);
            throw new ProviderUnavailableException("Unexpected failure connecting to Stripe: " + e.getMessage());
        }
    }

    @Override
    public PaymentOutcome queryPaymentStatus(String chargeId) {
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            PaymentIntent paymentIntent = PaymentIntent.retrieve(chargeId, options);
            if ("succeeded".equals(paymentIntent.getStatus())) {
                return PaymentOutcome.success(paymentIntent.getId(), paymentIntent.getId());
            } else {
                return new PaymentOutcome(false, paymentIntent.getStatus(), paymentIntent.getId(), paymentIntent.getClientSecret(), null, null);
            }
        } catch (StripeException e) {
            log.error("[STRIPE ACL] Error querying PaymentIntent {}: {}", chargeId, e.getMessage());
            throw new ProviderUnavailableException("Error querying status in Stripe: " + e.getMessage());
        }
    }
}
