package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.dto.result.PaymentResult;

/**
 * Use case for processing card payments through the integration gateway.
 */
public interface ProcessPaymentUseCase {

    /**
     * Executes a payment transaction with idempotency protection.
     *
     * @param command payment execution parameters
     * @return payment result
     */
    PaymentResult processPayment(ProcessPaymentCommand command);

    // --- Backward compatibility default method ---

    default PaymentResult procesarPago(ProcessPaymentCommand command) {
        return processPayment(command);
    }
}
