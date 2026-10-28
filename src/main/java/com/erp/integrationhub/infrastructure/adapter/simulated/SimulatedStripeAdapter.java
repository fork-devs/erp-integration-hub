package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.ChargeRequest;
import com.erp.integrationhub.domain.model.PaymentOutcome;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Simulated payment gateway adapter for development and testing profiles.
 */
public class SimulatedStripeAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedStripeAdapter.class);

    @Override
    public PaymentOutcome processCharge(ChargeRequest request) {
        log.warn("[SIMULATED] Processing simulated charge of {} {}: no upstream contact with Stripe",
                request.getCurrency(), request.getAmount());

        String chargeId = "pi_simulado_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String authCode = "AUTH_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return PaymentOutcome.success(chargeId, authCode);
    }

    @Override
    public PaymentOutcome queryPaymentStatus(String chargeId) {
        log.warn("[SIMULATED] Querying simulated payment status {}", chargeId);
        return PaymentOutcome.success(chargeId, "AUTH_SIM_CONFIRMED");
    }
}
