package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.command.ItemComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.usecase.EmitirComprobanteUseCase;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ComprobanteTipo;
import com.erp.integrationhub.domain.model.IdempotencyRecord;
import com.erp.integrationhub.domain.model.IdempotencyScope;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ElectronicInvoicingService implements EmitirComprobanteUseCase {

    private static final Logger log = LoggerFactory.getLogger(ElectronicInvoicingService.class);

    private final EmisorTributarioPort emisorTributarioPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final RetryQueueRepositoryPort retryQueueRepositoryPort;

    public ElectronicInvoicingService(
            EmisorTributarioPort emisorTributarioPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort) {
        this.emisorTributarioPort = emisorTributarioPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
    }

    @Override
    public ComprobanteResult emitirComprobante(EmitirComprobanteCommand command) {
        if (command.tenantId() == null || command.comprobanteId() == null) {
            throw new IllegalArgumentException("tenantId y comprobanteId son obligatorios");
        }

        // 1. Verificación de Idempotencia
        String idempotencyKey = command.idempotencyKey() != null ? command.idempotencyKey().trim() : command.comprobanteId().toString();
        Optional<IdempotencyRecord> existingRecord = idempotencyRepositoryPort.findByTenantAndScopeAndKey(
                command.tenantId(), IdempotencyScope.VOUCHER, idempotencyKey
        );

        if (existingRecord.isPresent()) {
            log.info("Petición idempotente detectada para comprobante {}. Retornando resultado cacheado.", command.comprobanteId());
            return deserializeResult(command.comprobanteId(), existingRecord.get().getResponsePayload(), true);
        }

        // 2. Mapeo al modelo de dominio agnóstico
        ComprobanteTipo tipo = ComprobanteTipo.valueOf(command.tipo().toUpperCase().trim());
        Moneda moneda = "USD".equalsIgnoreCase(command.moneda()) ? Moneda.USD : Moneda.PEN;

        List<ItemComprobante> items = command.items().stream()
                .map(this::toItemModel)
                .collect(Collectors.toList());

        ComprobanteParaDeclarar comprobante = new ComprobanteParaDeclarar(
                command.tenantId(),
                command.comprobanteId(),
                tipo,
                command.serie(),
                command.numero(),
                command.clienteTipoDoc(),
                command.clienteNumDoc(),
                command.clienteDenominacion(),
                command.clienteDireccion(),
                command.clienteEmail(),
                moneda,
                command.totalGravada(),
                command.totalIgv(),
                command.total(),
                items,
                command.fechaEmision()
        );

        // 3. Emisión hacia PSE / SUNAT con tolerancia a fallos
        ResultadoDeclaracion resultado;
        try {
            resultado = emisorTributarioPort.emitirComprobante(comprobante);
            attemptRepositoryPort.save(IntegrationAttempt.ok(
                    command.tenantId(),
                    ProviderType.NUBEFACT,
                    "EMIT_VOUCHER",
                    command.comprobanteId(),
                    1
            ));
        } catch (Exception e) {
            log.warn("Fallo al comunicar con PSE/SUNAT ({}: {}). Derivando a cola de reintento.", command.comprobanteId(), e.getMessage());
            
            attemptRepositoryPort.save(IntegrationAttempt.retry(
                    command.tenantId(),
                    ProviderType.NUBEFACT,
                    "EMIT_VOUCHER",
                    command.comprobanteId(),
                    1,
                    e.getMessage()
            ));

            // Guardar en cola de reintentos (Exponential backoff)
            retryQueueRepositoryPort.save(RetryTask.create(
                    command.tenantId(),
                    ProviderType.NUBEFACT,
                    "EMIT_VOUCHER",
                    command.comprobanteId(),
                    serializeCommand(command),
                    5
            ));

            resultado = ResultadoDeclaracion.pendiente("Servicio tributario no disponible temporalmente. Comprobante encolado para reintento automático.");
        }

        // 4. Guardar registro de idempotencia
        ComprobanteResult finalResult = new ComprobanteResult(
                command.comprobanteId(),
                resultado.exitoso(),
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
                command.tenantId(),
                IdempotencyScope.VOUCHER,
                idempotencyKey,
                serializeResult(finalResult)
        ));

        return finalResult;
    }

    private ItemComprobante toItemModel(ItemComprobanteCommand cmd) {
        return new ItemComprobante(
                cmd.unidadMedida(),
                cmd.descripcion(),
                cmd.cantidad(),
                cmd.valorUnitario(),
                cmd.precioUnitario(),
                cmd.igv(),
                cmd.total()
        );
    }

    private String serializeResult(ComprobanteResult result) {
        return String.format("{\"exitoso\":%b,\"estadoSunat\":\"%s\",\"codigo\":\"%s\",\"mensaje\":\"%s\",\"pdfUrl\":\"%s\",\"xmlUrl\":\"%s\",\"cdrUrl\":\"%s\",\"hashCdr\":\"%s\"}",
                result.exitoso(),
                result.estadoSunat() != null ? result.estadoSunat() : "",
                result.codigoRespuestaSunat() != null ? result.codigoRespuestaSunat() : "",
                result.mensajeSunat() != null ? result.mensajeSunat().replace("\"", "\\\"") : "",
                result.pdfUrl() != null ? result.pdfUrl() : "",
                result.xmlUrl() != null ? result.xmlUrl() : "",
                result.cdrUrl() != null ? result.cdrUrl() : "",
                result.hashCdr() != null ? result.hashCdr() : ""
        );
    }

    private ComprobanteResult deserializeResult(java.util.UUID comprobanteId, String payload, boolean fromCache) {
        if (payload == null) {
            return new ComprobanteResult(comprobanteId, true, "PENDIENTE_SUNAT", "0", "En proceso", null, null, null, null, fromCache);
        }
        boolean exitoso = payload.contains("\"exitoso\":true");
        String estado = extractJsonField(payload, "estadoSunat");
        String codigo = extractJsonField(payload, "codigo");
        String mensaje = extractJsonField(payload, "mensaje");
        String pdfUrl = extractJsonField(payload, "pdfUrl");
        String xmlUrl = extractJsonField(payload, "xmlUrl");
        String cdrUrl = extractJsonField(payload, "cdrUrl");
        String hashCdr = extractJsonField(payload, "hashCdr");

        return new ComprobanteResult(comprobanteId, exitoso, estado, codigo, mensaje, pdfUrl, xmlUrl, cdrUrl, hashCdr, fromCache);
    }

    private String serializeCommand(EmitirComprobanteCommand cmd) {
        return String.format("{\"tenantId\":\"%s\",\"comprobanteId\":\"%s\",\"tipo\":\"%s\",\"serie\":\"%s\",\"numero\":%d}",
                cmd.tenantId(), cmd.comprobanteId(), cmd.tipo(), cmd.serie(), cmd.numero());
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
