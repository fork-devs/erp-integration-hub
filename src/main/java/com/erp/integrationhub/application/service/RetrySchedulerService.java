package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ComprobanteTipo;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class RetrySchedulerService implements ProcessRetryQueueUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrySchedulerService.class);

    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final EmisorTributarioPort emisorTributarioPort;
    private final PasarelaDePagoPort pasarelaDePagoPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;

    public RetrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            EmisorTributarioPort emisorTributarioPort,
            PasarelaDePagoPort pasarelaDePagoPort,
            UnitOfWork unitOfWork) {
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.emisorTributarioPort = emisorTributarioPort;
        this.pasarelaDePagoPort = pasarelaDePagoPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.unitOfWork = unitOfWork;
    }

    @Override
    public int processPendingRetries(int batchSize) {
        List<RetryTask> pendingTasks = retryQueueRepositoryPort.findPendingTasks(Instant.now(), batchSize);
        int processed = 0;

        for (RetryTask task : pendingTasks) {
            try {
                log.info("Ejecutando reintento real de tarea {} para proveedor {} (Intento {}/{})",
                        task.getId(), task.getProvider(), task.getAttemptsCount() + 1, task.getMaxAttempts());

                if ("EMIT_VOUCHER".equalsIgnoreCase(task.getOperation())) {
                    executeVoucherRetry(task);
                } else if ("PROCESS_PAYMENT".equalsIgnoreCase(task.getOperation())) {
                    executePaymentRetry(task);
                } else {
                    log.warn("Operación desconocida en tarea de reintento: {}", task.getOperation());
                    unitOfWork.runInTransaction(() -> {
                        task.markCompleted();
                        retryQueueRepositoryPort.save(task);
                    });
                }

                processed++;
            } catch (Exception e) {
                log.warn("Fallo al reintentar tarea {}: {}", task.getId(), e.getMessage());
                unitOfWork.runInTransaction(() -> {
                    task.recordFailure(e.getMessage());
                    retryQueueRepositoryPort.save(task);

                    attemptRepositoryPort.save(IntegrationAttempt.retry(
                            task.getTenantId(),
                            task.getProvider(),
                            task.getOperation(),
                            task.getReferenceId(),
                            task.getAttemptsCount(),
                            e.getMessage()
                    ));
                });
            }
        }

        return processed;
    }

    private void executeVoucherRetry(RetryTask task) throws Exception {
        EmitirComprobanteCommand cmd = objectMapper.readValue(task.getPayload(), EmitirComprobanteCommand.class);

        ComprobanteTipo tipo = ComprobanteTipo.valueOf(cmd.tipo().toUpperCase().trim());
        Moneda moneda = "USD".equalsIgnoreCase(cmd.moneda()) ? Moneda.USD : Moneda.PEN;

        List<ItemComprobante> items = cmd.items().stream()
                .map(i -> new ItemComprobante(
                        i.unidadMedida(),
                        i.descripcion(),
                        i.cantidad(),
                        i.valorUnitario(),
                        i.precioUnitario(),
                        i.igv(),
                        i.total()
                ))
                .collect(Collectors.toList());

        ComprobanteParaDeclarar comprobante = new ComprobanteParaDeclarar(
                cmd.tenantId(),
                cmd.comprobanteId(),
                tipo,
                cmd.serie(),
                cmd.numero(),
                cmd.clienteTipoDoc(),
                cmd.clienteNumDoc(),
                cmd.clienteDenominacion(),
                cmd.clienteDireccion(),
                cmd.clienteEmail(),
                moneda,
                cmd.totalGravada(),
                cmd.totalIgv(),
                cmd.total(),
                items,
                cmd.fechaEmision()
        );

        ResultadoDeclaracion resultado = emisorTributarioPort.emitirComprobante(comprobante);

        if (!resultado.exitoso()) {
            // El proveedor NO emito el comprobante: reintentar es seguro.
            throw new RuntimeException("Respuesta no exitosa de APISUNAT/SUNAT: " + resultado.mensajeSunat());
        }

        // El proveedor YA emitio el comprobante. A partir de aqui no se
        // puede reenviar: una segunda emision seria un comprobante
        // duplicado, que es un problema mucho mayor que un registro local
        // que falta.
        //
        // El estado de la tarea y el registro de idempotencia se confirman
        // juntos: antes, un fallo al escribir la idempotencia dejaba la
        // tarea sin marcar, y el planificador la reprogramaba para reenviar
        // el comprobante a SUNAT, que ya lo habia emitido.
        // Actualizar el registro de idempotencia con los datos reales de SUNAT (Issue 2 resuelto)
        ComprobanteResult finalResult = new ComprobanteResult(
                cmd.comprobanteId(),
                true,
                resultado.estadoSunat(),
                resultado.codigoRespuestaSunat(),
                resultado.mensajeSunat(),
                resultado.pdfUrl(),
                resultado.xmlUrl(),
                resultado.cdrUrl(),
                resultado.hashCdr(),
                false
        );

        // El payload se serializa antes del limite transaccional: la lambda
        // no puede propagar una excepcion comprobada, y un fallo de
        // serializacion no debe confundirse con un fallo de persistencia.
        final String idempotencyPayload;
        try {
            idempotencyPayload = objectMapper.writeValueAsString(finalResult);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo serializar el resultado del comprobante", e);
        }

        try {
            unitOfWork.runInTransaction(() -> {
                task.markCompleted();
                retryQueueRepositoryPort.save(task);

                idempotencyRepositoryPort.save(IdempotencyRecord.of(
                        cmd.tenantId(),
                        IdempotencyScope.VOUCHER,
                        cmd.idempotencyKey() != null ? cmd.idempotencyKey() : cmd.comprobanteId().toString(),
                        idempotencyPayload
                ));

                attemptRepositoryPort.save(IntegrationAttempt.ok(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount() + 1
                ));
            });
        } catch (Exception e) {
            // El comprobante existe en SUNAT, pero no se pudo registrar aqui.
            // La tarea pasa a la cola de muertos, que es terminal: NO se
            // reprograma, de modo que el comprobante no se reenvia. La falta
            // del registro queda como evidencia operativa, que exige
            // atencion manual, y no como un motivo para duplicar el
            // comprobante.
            log.error("El comprobante {} fue emitido por el proveedor pero no se pudo registrar localmente: {}",
                    cmd.comprobanteId(), e.getMessage());

            unitOfWork.runInTransaction(() -> {
                task.markDeadLetter("Comprobante emitido por el proveedor, pero no se pudo registrar localmente: "
                        + e.getMessage());
                retryQueueRepositoryPort.save(task);

                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        task.getTenantId(),
                        task.getProvider(),
                        task.getOperation(),
                        task.getReferenceId(),
                        task.getAttemptsCount() + 1,
                        "Comprobante emitido por el proveedor, pero no se pudo registrar localmente: " + e.getMessage()
                ));
            });
        }
    }

    private void executePaymentRetry(RetryTask task) {
        unitOfWork.runInTransaction(() -> {
            task.markCompleted();
            retryQueueRepositoryPort.save(task);
            attemptRepositoryPort.save(IntegrationAttempt.ok(
                    task.getTenantId(),
                    task.getProvider(),
                    task.getOperation(),
                    task.getReferenceId(),
                    task.getAttemptsCount() + 1
            ));
        });
    }
}
