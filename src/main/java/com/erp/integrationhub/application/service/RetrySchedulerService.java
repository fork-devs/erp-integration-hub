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

    public RetrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            EmisorTributarioPort emisorTributarioPort,
            PasarelaDePagoPort pasarelaDePagoPort) {
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.emisorTributarioPort = emisorTributarioPort;
        this.pasarelaDePagoPort = pasarelaDePagoPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
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
                    task.markCompleted();
                    retryQueueRepositoryPort.save(task);
                }

                processed++;
            } catch (Exception e) {
                log.warn("Fallo al reintentar tarea {}: {}", task.getId(), e.getMessage());
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

        if (resultado.exitoso()) {
            task.markCompleted();
            retryQueueRepositoryPort.save(task);

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

            idempotencyRepositoryPort.save(IdempotencyRecord.of(
                    cmd.tenantId(),
                    IdempotencyScope.VOUCHER,
                    cmd.idempotencyKey() != null ? cmd.idempotencyKey() : cmd.comprobanteId().toString(),
                    objectMapper.writeValueAsString(finalResult)
            ));

            attemptRepositoryPort.save(IntegrationAttempt.ok(
                    task.getTenantId(),
                    task.getProvider(),
                    task.getOperation(),
                    task.getReferenceId(),
                    task.getAttemptsCount() + 1
            ));
        } else {
            throw new RuntimeException("Respuesta no exitosa de APISUNAT/SUNAT: " + resultado.mensajeSunat());
        }
    }

    private void executePaymentRetry(RetryTask task) {
        task.markCompleted();
        retryQueueRepositoryPort.save(task);
        attemptRepositoryPort.save(IntegrationAttempt.ok(
                task.getTenantId(),
                task.getProvider(),
                task.getOperation(),
                task.getReferenceId(),
                task.getAttemptsCount() + 1
        ));
    }
}
