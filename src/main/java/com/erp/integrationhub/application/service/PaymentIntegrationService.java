package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcesarPagoUseCase;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PaymentIntegrationService implements ProcesarPagoUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntegrationService.class);

    private final PasarelaDePagoPort pasarelaDePagoPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;

    public PaymentIntegrationService(
            PasarelaDePagoPort pasarelaDePagoPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort) {
        this.pasarelaDePagoPort = pasarelaDePagoPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
    }

    @Override
    public PagoResult procesarPago(ProcesarPagoCommand command) {
        if (command.tenantId() == null || command.paymentId() == null || command.idempotencyKey() == null) {
            throw new IllegalArgumentException("tenantId, paymentId e idempotencyKey son obligatorios");
        }

        // 1. Verificación de Idempotencia
        Optional<IdempotencyRecord> existing = idempotencyRepositoryPort.findByTenantAndScopeAndKey(
                command.tenantId(), IdempotencyScope.PAYMENT, command.idempotencyKey()
        );

        if (existing.isPresent()) {
            log.info("Cobro idempotente detectado para pago {}. Retornando resultado cacheado.", command.paymentId());
            return deserializeResult(command.paymentId(), existing.get().getResponsePayload(), true);
        }

        // 2. Mapeo a entidad de dominio
        Moneda moneda = "USD".equalsIgnoreCase(command.moneda()) ? Moneda.USD : Moneda.PEN;
        SolicitudCobro solicitud = new SolicitudCobro(
                command.tenantId(),
                command.paymentId(),
                command.monto(),
                moneda,
                command.tokenTarjeta(),
                command.descripcion(),
                command.clienteEmail(),
                command.idempotencyKey()
        );

        // 3. Procesar cobro en pasarela (Stripe)
        ResultadoPago resultado;
        try {
            resultado = pasarelaDePagoPort.procesarCobro(solicitud);
            attemptRepositoryPort.save(IntegrationAttempt.ok(
                    command.tenantId(),
                    ProviderType.STRIPE,
                    "CHARGE",
                    command.paymentId(),
                    1
            ));
        } catch (Exception e) {
            log.error("Fallo al procesar cobro en pasarela: {}", e.getMessage());
            attemptRepositoryPort.save(IntegrationAttempt.retry(
                    command.tenantId(),
                    ProviderType.STRIPE,
                    "CHARGE",
                    command.paymentId(),
                    1,
                    e.getMessage()
            ));
            resultado = ResultadoPago.fallido(e.getMessage());
        }

        // 4. Guardar respuesta en tabla de idempotencia
        PagoResult finalResult = new PagoResult(
                command.paymentId(),
                resultado.exitoso(),
                resultado.estado(),
                resultado.chargeId(),
                resultado.clientSecret(),
                resultado.codigoAutorizacion(),
                resultado.mensajeError(),
                false
        );

        idempotencyRepositoryPort.save(IdempotencyRecord.of(
                command.tenantId(),
                IdempotencyScope.PAYMENT,
                command.idempotencyKey(),
                serializeResult(finalResult)
        ));

        return finalResult;
    }

    private String serializeResult(PagoResult result) {
        return String.format("{\"exitoso\":%b,\"estado\":\"%s\",\"chargeId\":\"%s\",\"clientSecret\":\"%s\",\"codigoAutorizacion\":\"%s\",\"mensajeError\":\"%s\"}",
                result.exitoso(),
                result.estado() != null ? result.estado() : "",
                result.chargeId() != null ? result.chargeId() : "",
                result.clientSecret() != null ? result.clientSecret() : "",
                result.codigoAutorizacion() != null ? result.codigoAutorizacion() : "",
                result.mensajeError() != null ? result.mensajeError().replace("\"", "\\\"") : ""
        );
    }

    private PagoResult deserializeResult(java.util.UUID paymentId, String payload, boolean fromCache) {
        if (payload == null) {
            return new PagoResult(paymentId, false, "FAILED", null, null, null, "Error deserializando", fromCache);
        }
        boolean exitoso = payload.contains("\"exitoso\":true");
        String estado = extractJsonField(payload, "estado");
        String chargeId = extractJsonField(payload, "chargeId");
        String clientSecret = extractJsonField(payload, "clientSecret");
        String codigo = extractJsonField(payload, "codigoAutorizacion");
        String error = extractJsonField(payload, "mensajeError");

        return new PagoResult(paymentId, exitoso, estado, chargeId, clientSecret, codigo, error, fromCache);
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
