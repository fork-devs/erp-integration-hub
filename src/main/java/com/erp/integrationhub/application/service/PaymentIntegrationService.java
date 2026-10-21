package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ProcesarPagoCommand;
import com.erp.integrationhub.application.logging.SecurityEventLogger;
import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcesarPagoUseCase;
import com.erp.integrationhub.domain.exception.IdempotencyConflictException;
import com.erp.integrationhub.domain.model.IdempotencyKeyState;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;
import com.erp.integrationhub.domain.util.PayloadFingerprint;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PaymentIntegrationService implements ProcesarPagoUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntegrationService.class);

    private final PasarelaDePagoPort pasarelaDePagoPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final UnitOfWork unitOfWork;

    public PaymentIntegrationService(
            PasarelaDePagoPort pasarelaDePagoPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this.pasarelaDePagoPort = pasarelaDePagoPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public PagoResult procesarPago(ProcesarPagoCommand command) {
        if (command.tenantId() == null || command.paymentId() == null || command.idempotencyKey() == null) {
            throw new IllegalArgumentException("tenantId, paymentId e idempotencyKey son obligatorios");
        }

        // 1. Huella de la carga util. Es lo que distingue la peticion que se
        // repite de otra peticion con la misma clave.
        String huella = PayloadFingerprint.of(cargaUtilDe(command));

        // 2. Estado de la clave. Si ya hay un resultado DEFINITIVO, se
        // devuelve; si la clave esta EN CURSO, otra peticion la reclamo y no
        // hay que volver a llamar al proveedor; si esta LIBERADA, se puede
        // reintentar.
        Optional<IdempotencyRecord> existing = idempotencyRepositoryPort.findByTenantAndScopeAndKey(
                command.tenantId(), IdempotencyScope.PAYMENT, command.idempotencyKey()
        );

        if (existing.isPresent()) {
            IdempotencyRecord registro = existing.get();

            if (!registro.mismaPeticion(huella)) {
                // La misma clave con otra carga util NO es la misma peticion.
                // Devolver el resultado anterior seria no cobrar la segunda.
                throw new IdempotencyConflictException(
                        "La clave de idempotencia ya se uso para una peticion distinta");
            }

            if (registro.getState().resultadoDefinitivo()) {
                log.info("Cobro idempotente detectado para pago {}. Retornando resultado almacenado.",
                        command.paymentId());
                return deserializeResult(command.paymentId(), registro.getResponsePayload(), true);
            }

            if (registro.getState() == IdempotencyKeyState.IN_PROGRESS) {
                // La clave esta reclamada: otra peticion esta contacting al
                // proveedor con ella. Volver a llamar seria cobrar dos veces.
                throw new IdempotencyConflictException(
                        "La operacion con esta clave de idempotencia ya esta en curso");
            }

            // LIBERADA: el intento anterior fallo de forma transitoria. Se
            // vuelve a intentar.
            log.info("Reintentando el cobro {} con una clave de idempotencia liberada.", command.paymentId());
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

        // 3. RECLAMAR la clave, ANTES de contactar al proveedor. La
        // unicidad de (taller, alcance, clave) la impone la base de datos:
        // dos peticiones simultaneas con la misma clave no pueden reclamar
        // las dos, y la que pierde NO llama al proveedor. Sin esta
        // reclamacion previa, dos peticiones simultaneas cobraban dos veces.
        final IdempotencyRecord claimed;
        try {
            claimed = unitOfWork.callInTransaction(() -> idempotencyRepositoryPort.claim(
                    command.tenantId(), IdempotencyScope.PAYMENT, command.idempotencyKey(), huella));
        } catch (IdempotencyConflictException e) {
            log.info("La clave de idempotencia {} ya esta reclamada: no se vuelve a cobrar.",
                    command.idempotencyKey());
            throw e;
        }

        // 4. Procesar cobro en pasarela (Stripe). La llamada al proveedor NO
        // ocurre dentro de la transaccion: es una llamada externa, lenta y
        // fuera del control del sistema, y mantener abierta una transaccion
        // mientras espera bloquearia recursos de la base de datos sin
        // ninguna garantia.
        ResultadoPago resultadoDelProveedor;
        try {
            resultadoDelProveedor = pasarelaDePagoPort.procesarCobro(solicitud);
        } catch (Exception e) {
            log.error("Fallo al procesar cobro en pasarela: {}", e.getMessage());
            resultadoDelProveedor = ResultadoPago.fallido(e.getMessage());
        }
        final ResultadoPago resultado = resultadoDelProveedor;

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

        // 5. El registro del intento y la ACTUALIZACION del registro de
        // idempotencia se confirman juntos. La actualizacion va por la clave
        // de negocio, de modo que no inserta una fila nueva: el resultado
        // llega a la fila que ya fue reclamada.
        unitOfWork.runInTransaction(() -> {
            attemptRepositoryPort.save(resultado.exitoso()
                    ? IntegrationAttempt.ok(
                            command.tenantId(),
                            ProviderType.STRIPE,
                            "CHARGE",
                            command.paymentId(),
                            1)
                    : IntegrationAttempt.retry(
                            command.tenantId(),
                            ProviderType.STRIPE,
                            "CHARGE",
                            command.paymentId(),
                            1,
                            resultado.mensajeError()));

            String payload = serializeResult(finalResult);
            String hash = Integer.toHexString(payload.hashCode());

            // Un fallo NO es un resultado definitivo: la clave se libera
            // para que un reintento pueda volver a intentarlo. Con el
            // resultado cacheado como definitivo, la peticion del usuario se
            // perdia para siempre.
            idempotencyRepositoryPort.updateByKey(
                    command.tenantId(),
                    IdempotencyScope.PAYMENT,
                    command.idempotencyKey(),
                    resultado.exitoso() ? claimed.completed(payload, hash) : claimed.released());
        });

        SecurityEventLogger.registrar("cobro_procesado", command.tenantId(), null, null,
                finalResult.exitoso() ? "ok" : "fallo", java.util.Map.of("pago", command.paymentId().toString()));

        return finalResult;
    }

    /**
     * La carga util de la peticion de cobro. Es lo que se huella para
     * distinguir la peticion que se repite de otra con la misma clave.
     */
    private String cargaUtilDe(ProcesarPagoCommand command) {
        return String.join("|",
                String.valueOf(command.tenantId()),
                String.valueOf(command.paymentId()),
                String.valueOf(command.monto()),
                String.valueOf(command.moneda()),
                String.valueOf(command.tokenTarjeta()),
                String.valueOf(command.descripcion()),
                String.valueOf(command.clienteEmail()));
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
