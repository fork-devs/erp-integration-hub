package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ChargeRequest;
import com.erp.integrationhub.domain.model.PaymentOutcome;

/**
 * Port outbound to external payment gateway (e.g., Stripe).
 */
public interface PaymentGatewayPort {

    /**
     * Executes a credit/debit card charge through the payment gateway.
     *
     * @param request charge request containing amount, currency, and card token
     * @return outcome of the payment execution
     */
    PaymentOutcome processCharge(ChargeRequest request);

    /**
     * Queries the status of an existing charge by provider ID.
     *
     * @param chargeId identifier returned by payment gateway
     * @return updated outcome of the payment
     */
    PaymentOutcome queryPaymentStatus(String chargeId);

    // --- Backward compatibility default methods ---

    default PaymentOutcome procesarCobro(ChargeRequest solicitud) {
        return processCharge(solicitud);
    }

    default PaymentOutcome consultarEstadoPago(String chargeId) {
        return queryPaymentStatus(chargeId);
    }
}
