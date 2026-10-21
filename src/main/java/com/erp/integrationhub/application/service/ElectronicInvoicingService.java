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
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ElectronicInvoicingService implements EmitirComprobanteUseCase {

    private static final Logger log = LoggerFactory.getLogger(ElectronicInvoicingService.class);

    private final EmisorTributarioPort emisorTributarioPort;
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final ObjectMapper objectMapper;
    private final UnitOfWork unitOfWork;

    public ElectronicInvoicingService(
            EmisorTributarioPort emisorTributarioPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            UnitOfWork unitOfWork) {
        this.emisorTributarioPort = emisorTributarioPort;
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.unitOfWork = unitOfWork;
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
            ComprobanteResult cachedResult = deserializeResult(command.comprobanteId(), existingRecord.get().getResponsePayload(), true);
            
            // Si el comprobante estaba en estado PENDIENTE_SUNAT, verificar si ya fue procesado en SUNAT
            if ("PENDIENTE_SUNAT".equalsIgnoreCase(cachedResult.estadoSunat()) || "PENDIENTE".equalsIgnoreCase(cachedResult.estadoSunat())) {
                try {
                    ResultadoDeclaracion statusCheck = emisorTributarioPort.consultarEstadoComprobante(
                            command.tipo(), command.serie(), command.numero()
                    );
                    if (statusCheck.exitoso() && "ACEPTADO".equalsIgnoreCase(statusCheck.estadoSunat())) {
                        ComprobanteResult updatedResult = new ComprobanteResult(
                                command.comprobanteId(),
                                true,
                                statusCheck.estadoSunat(),
                                statusCheck.codigoRespuestaSunat(),
                                statusCheck.mensajeSunat(),
                                statusCheck.pdfUrl(),
                                statusCheck.xmlUrl(),
                                statusCheck.cdrUrl(),
                                statusCheck.hashCdr(),
                                false
                        );
                        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                                command.tenantId(),
                                IdempotencyScope.VOUCHER,
                                idempotencyKey,
                                serializeResult(updatedResult)
                        )));
                        return updatedResult;
                    }
                } catch (Exception ignored) {}
            }

            log.info("Petición idempotente detectada para comprobante {}. Retornando resultado.", command.comprobanteId());
            return cachedResult;
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

        // 3. Emisión hacia PSE / SUNAT con tolerancia a fallos.
        //
        // La llamada al proveedor no ocurre dentro del limite transaccional:
        // es una llamada externa, lenta y fuera del control del sistema, y
        // mantener una transaccion abierta mientras espera bloquearia
        // recursos de la base de datos sin ninguna garantia.
        ResultadoDeclaracion resultado;
        boolean emitido = false;
        try {
            resultado = emisorTributarioPort.emitirComprobante(comprobante);
            emitido = true;
        } catch (Exception e) {
            log.warn("Fallo al comunicar con PSE/SUNAT ({}: {}). Derivando a cola de reintento.", command.comprobanteId(), e.getMessage());

            // El registro del intento y el encolado del reintento se
            // confirman juntos: un intento sin tarea de reintento es un
            // cobro perdido, y una tarea de reintento sin su intento es un
            // reenvio sin trazabilidad.
            unitOfWork.runInTransaction(() -> {
                attemptRepositoryPort.save(IntegrationAttempt.retry(
                        command.tenantId(),
                        ProviderType.APISUNAT,
                        "EMIT_VOUCHER",
                        command.comprobanteId(),
                        1,
                        e.getMessage()
                ));

                // Guardar en cola de reintentos (Exponential backoff con payload completo serializado)
                retryQueueRepositoryPort.save(RetryTask.create(
                        command.tenantId(),
                        ProviderType.APISUNAT,
                        "EMIT_VOUCHER",
                        command.comprobanteId(),
                        serializeCommand(command),
                        5
                ));
            });

            resultado = ResultadoDeclaracion.pendiente("Servicio tributario no disponible temporalmente. Comprobante encolado para reintento automático.");
        }

        if (emitido) {
            unitOfWork.runInTransaction(() -> attemptRepositoryPort.save(IntegrationAttempt.ok(
                    command.tenantId(),
                    ProviderType.APISUNAT,
                    "EMIT_VOUCHER",
                    command.comprobanteId(),
                    1
            )));
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

        unitOfWork.runInTransaction(() -> idempotencyRepositoryPort.save(IdempotencyRecord.of(
                command.tenantId(),
                IdempotencyScope.VOUCHER,
                idempotencyKey,
                serializeResult(finalResult)
        )));

        return finalResult;
    }

    @Override
    public ComprobanteResult anularComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero, String motivo) {
        if (tenantId == null || comprobanteId == null) {
            throw new IllegalArgumentException("tenantId y comprobanteId son obligatorios");
        }
        log.info("[APISUNAT] Anulando comprobante {} {}-{} para tenant {}", tipo, serie, numero, tenantId);

        ResultadoDeclaracion resultado;
        try {
            resultado = emisorTributarioPort.anularComprobante(tipo, serie, numero, motivo);
            unitOfWork.runInTransaction(() -> attemptRepositoryPort.save(IntegrationAttempt.ok(
                    tenantId,
                    ProviderType.APISUNAT,
                    "VOID_VOUCHER",
                    comprobanteId,
                    1
            )));
        } catch (Exception e) {
            log.warn("Error al anular comprobante en APISUNAT ({}): {}", comprobanteId, e.getMessage());
            resultado = ResultadoDeclaracion.rechazado("500", "Fallo al comunicar anulación con APISUNAT: " + e.getMessage());
        }

        return new ComprobanteResult(
                comprobanteId,
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
    }

    @Override
    public ComprobanteResult consultarEstadoComprobante(UUID tenantId, UUID comprobanteId, String tipo, String serie, int numero) {
        if (tenantId == null || comprobanteId == null) {
            throw new IllegalArgumentException("tenantId y comprobanteId son obligatorios");
        }
        log.info("[APISUNAT] Consultando estado de comprobante {} {}-{} para tenant {}", tipo, serie, numero, tenantId);

        ResultadoDeclaracion resultado;
        try {
            resultado = emisorTributarioPort.consultarEstadoComprobante(tipo, serie, numero);
        } catch (Exception e) {
            log.warn("Error al consultar estado en APISUNAT ({}): {}", comprobanteId, e.getMessage());
            resultado = ResultadoDeclaracion.rechazado("500", "Fallo al consultar estado con APISUNAT: " + e.getMessage());
        }

        return new ComprobanteResult(
                comprobanteId,
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
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar resultado de comprobante", e);
        }
    }

    private ComprobanteResult deserializeResult(UUID comprobanteId, String payload, boolean fromCache) {
        if (payload == null || payload.isBlank()) {
            return new ComprobanteResult(comprobanteId, true, "PENDIENTE_SUNAT", "0", "En proceso", null, null, null, null, fromCache);
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(payload);
            boolean exitoso = root.path("exitoso").asBoolean(true);
            String estado = root.path("estadoSunat").asText(root.path("estado").asText("ACEPTADA"));
            String codigo = root.path("codigoRespuestaSunat").asText(root.path("codigo").asText("0"));
            String mensaje = root.path("mensajeSunat").asText(root.path("mensaje").asText("Comprobante procesado"));
            String pdfUrl = root.path("pdfUrl").asText(null);
            String xmlUrl = root.path("xmlUrl").asText(null);
            String cdrUrl = root.path("cdrUrl").asText(null);
            String hashCdr = root.path("hashCdr").asText(null);

            return new ComprobanteResult(
                    comprobanteId,
                    exitoso,
                    estado,
                    codigo,
                    mensaje,
                    pdfUrl,
                    xmlUrl,
                    cdrUrl,
                    hashCdr,
                    fromCache
            );
        } catch (Exception e) {
            log.warn("Error al deserializar resultado cacheado: {}", e.getMessage());
            return new ComprobanteResult(comprobanteId, true, "PENDIENTE_SUNAT", "0", "En proceso", null, null, null, null, fromCache);
        }
    }

    private String serializeCommand(EmitirComprobanteCommand cmd) {
        try {
            return objectMapper.writeValueAsString(cmd);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar comando para reintento", e);
        }
    }
}
