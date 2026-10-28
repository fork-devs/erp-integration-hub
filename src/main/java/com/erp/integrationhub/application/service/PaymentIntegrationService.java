package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcessPaymentCommand;
import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.erp.integrationhub.application.logging.SecurityEventLogger;
import com.erp.integrationhub.application.usecase.ProcessPaymentUseCase;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.ChargeRequest;
import com.erp.integrationhub.domain.model.Currency;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.PaymentOutcome;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.util.PayloadFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service for processing credit/debit card payments via external gateway.
 *
 * <p>Enforces strict idempotency claiming, payload fingerprint verification, and atomic
 * state transitions to ensure double-charging never occurs.
 */
public class PaymentIntegrationService implements ProcessPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntegrationService.class);

    private final PaymentGatewayPort paymentGatewayPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final UnitOfWork unitOfWork;

    public PaymentIntegrationService(
            PaymentGatewayPort paymentGatewayPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this.paymentGatewayPort = paymentGatewayPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public PaymentResult processPayment(ProcessPaymentCommand command) {
        if (command.tenantId() == null || command.paymentId() == null || command.idempotencyKey() == null) {
            throw new IllegalArgumentException("tenantId, paymentId, and idempotencyKey are required");
        }

        // 1. Calculate cryptographic request payload fingerprint
        String fingerprint = PayloadFingerprint.of(buildPayloadFingerprintSource(command));

        // 2. Inspect existing idempotency record
        Optional<IdempotencyRecord> existing = idempotencyRepositoryPort.findByTenantAndScopeAndKey(
                command.tenantId(), IdempotencyScope.PAYMENT, command.idempotencyKey()
        );

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if (!record.isSameRequest(fingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency key has already been used with a different request payload");
            }

            if (record.getState().isDefinitiveResult()) {
                log.info("Idempotent payment detected for {}. Returning stored result.", command.paymentId());
                return deserializeResult(command.paymentId(), record.getResponsePayload(), true);
            }

            if (record.getState() == IdempotencyKeyState.IN_PROGRESS) {
                throw new IdempotencyConflictException(
                        "Operation with this idempotency key is already in progress");
            }

            log.info("Retrying payment {} with previously released idempotency key.", command.paymentId());
        }

        // 3. Map to domain entity and claim idempotency key before contacting provider
        Currency currency = "USD".equalsIgnoreCase(command.currency()) ? Currency.USD : Currency.PEN;
        ChargeRequest chargeRequest = new ChargeRequest(
                command.tenantId(),
                command.paymentId(),
                command.amount(),
                currency,
                command.cardToken(),
                command.description(),
                command.customerEmail(),
                command.idempotencyKey()
        );

        final IdempotencyRecord claimed;
        try {
            claimed = unitOfWork.callInTransaction(() -> idempotencyRepositoryPort.claim(
                    command.tenantId(), IdempotencyScope.PAYMENT, command.idempotencyKey(), fingerprint));
        } catch (IdempotencyConflictException e) {
            log.info("Idempotency key {} is already claimed: preventing duplicate charge.",
                    command.idempotencyKey());
            throw e;
        }

        // 4. Invoke external payment gateway outside transaction boundary
        PaymentOutcome gatewayOutcome;
        try {
            gatewayOutcome = paymentGatewayPort.processCharge(chargeRequest);
        } catch (Exception e) {
            log.error("Failed to process charge on payment gateway: {}", e.getMessage());
            gatewayOutcome = PaymentOutcome.failure(e.getMessage());
        }
        final PaymentOutcome outcome = gatewayOutcome;

        PaymentResult finalResult = new PaymentResult(
                command.paymentId(),
                outcome.successful(),
                outcome.status(),
                outcome.chargeId(),
                outcome.clientSecret(),
                outcome.authorizationCode(),
                outcome.errorMessage(),
                false
        );

        // 5. Update attempt log and idempotency record atomically
        unitOfWork.runInTransaction(() -> {
            int attemptCount = 1 + attemptRepositoryPort.countAttempts(
                    command.tenantId(), ProviderType.STRIPE, "CHARGE", command.paymentId());

            attemptRepositoryPort.save(outcome.successful()
                    ? IntegrationAttempt.ok(
                            command.tenantId(),
                            ProviderType.STRIPE,
                            "CHARGE",
                            command.paymentId(),
                            attemptCount)
                    : IntegrationAttempt.retry(
                            command.tenantId(),
                            ProviderType.STRIPE,
                            "CHARGE",
                            command.paymentId(),
                            attemptCount,
                            outcome.errorMessage()));

            String payload = serializeResult(finalResult);
            String hash = Integer.toHexString(payload.hashCode());

            idempotencyRepositoryPort.updateByKey(
                    command.tenantId(),
                    IdempotencyScope.PAYMENT,
                    command.idempotencyKey(),
                    outcome.successful() ? claimed.completed(payload, hash) : claimed.released());
        });

        SecurityEventLogger.registrar("cobro_procesado", command.tenantId(), null, null,
                finalResult.successful() ? "ok" : "fallo", java.util.Map.of("pago", command.paymentId().toString()));

        return finalResult;
    }

    private String buildPayloadFingerprintSource(ProcessPaymentCommand command) {
        return String.join("|",
                String.valueOf(command.tenantId()),
                String.valueOf(command.paymentId()),
                String.valueOf(command.amount()),
                String.valueOf(command.currency()),
                String.valueOf(command.cardToken()),
                String.valueOf(command.description()),
                String.valueOf(command.customerEmail()));
    }

    private String serializeResult(PaymentResult result) {
        return String.format("{\"successful\":%b,\"status\":\"%s\",\"chargeId\":\"%s\",\"clientSecret\":\"%s\",\"authorizationCode\":\"%s\",\"errorMessage\":\"%s\"}",
                result.successful(),
                result.status() != null ? result.status() : "",
                result.chargeId() != null ? result.chargeId() : "",
                result.clientSecret() != null ? result.clientSecret() : "",
                result.authorizationCode() != null ? result.authorizationCode() : "",
                result.errorMessage() != null ? result.errorMessage().replace("\"", "\\\"") : ""
        );
    }

    private PaymentResult deserializeResult(UUID paymentId, String payload, boolean fromCache) {
        if (payload == null) {
            return new PaymentResult(paymentId, false, "FAILED", null, null, null, "Error deserializing payload", fromCache);
        }
        boolean successful = payload.contains("\"successful\":true") || payload.contains("\"exitoso\":true");
        String status = extractJsonField(payload, "status");
        if (status == null) {
            status = extractJsonField(payload, "estado");
        }
        String chargeId = extractJsonField(payload, "chargeId");
        String clientSecret = extractJsonField(payload, "clientSecret");
        String code = extractJsonField(payload, "authorizationCode");
        if (code == null) {
            code = extractJsonField(payload, "codigoAutorizacion");
        }
        String error = extractJsonField(payload, "errorMessage");
        if (error == null) {
            error = extractJsonField(payload, "mensajeError");
        }

        return new PaymentResult(paymentId, successful, status, chargeId, clientSecret, code, error, fromCache);
    }

    private String extractJsonField(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
