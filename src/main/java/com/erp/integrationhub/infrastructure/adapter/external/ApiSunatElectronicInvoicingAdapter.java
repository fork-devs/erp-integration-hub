package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ApiSunatElectronicInvoicingAdapter implements EmisorTributarioPort {

    private static final Logger log = LoggerFactory.getLogger(ApiSunatElectronicInvoicingAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiSunatElectronicInvoicingAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://sandbox.apisunat.pe";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ResultadoDeclaracion emitirComprobante(ComprobanteParaDeclarar comprobante) {
        log.info("[APISUNAT ACL] Declarando {} {}-{} a SUNAT para cliente {} ({})",
                comprobante.getTipo(), comprobante.getSerie(), comprobante.getNumero(),
                comprobante.getClienteDenominacion(), comprobante.getClienteNumDoc());

        // Modo sandbox si la credencial es de prueba o mock
        // Llamada HTTP Real al API v3 de APISUNAT
        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "api/v3/documents" : baseUrl + "/api/v3/documents";
            String requestJson = buildApiSunatJsonPayload(comprobante);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[APISUNAT ACL] Respuesta HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return parseApiSunatSuccessResponse(response.body());
            } else {
                String errorMsg = extractMessageFromJson(response.body());
                log.warn("[APISUNAT ACL] Error devuelto por APISUNAT/SUNAT (HTTP {}): {}", response.statusCode(), errorMsg);
                return ResultadoDeclaracion.rechazado(String.valueOf(response.statusCode()), errorMsg);
            }

        } catch (Exception e) {
            log.error("[APISUNAT ACL] Fallo de conexion con API APISUNAT: {}", e.getMessage());
            throw new ProviderUnavailableException("Error conectando con APISUNAT: " + e.getMessage());
        }
    }

    @Override
    public ResultadoDeclaracion anularComprobante(String tipo, String serie, int numero, String motivo) {
        log.info("[APISUNAT ACL] Solicitando anulación/baja de {} {}-{} por motivo: {}", tipo, serie, numero, motivo);

        try {
            boolean isBoleta = tipo != null && tipo.toLowerCase().contains("boleta");
            String endpoint = isBoleta ? "api/v3/daily-summary" : "api/v3/voided";
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + endpoint : baseUrl + "/" + endpoint;

            ObjectNode root = objectMapper.createObjectNode();
            String docTipo = isBoleta ? "boleta" : (tipo != null ? tipo.toLowerCase().replace(" ", "_") : "factura");
            root.put("documento", docTipo);
            root.put("serie", serie);
            root.put("numero", numero);
            root.put("motivo", motivo != null ? motivo : "Anulación solicitada");
            if (isBoleta) {
                root.put("accion", "anulacion");
            }

            // La anulacion EXIGE el ticket de comunicacion del comprobante:
            // sin el, la autoridad fiscal no sabe que documento se esta
            // anulando, y rechaza la solicitud. El ticket se obtiene del
            // endpoint de consulta y se referencia en la peticion.
            String ticket = obtenerTicketDeComunicacion(docTipo, serie, numero);
            if (ticket == null || ticket.isBlank()) {
                log.warn("[APISUNAT ACL] No se pudo obtener el ticket de comunicación de {}-{}-{}: "
                        + "la anulación no puede continuar", docTipo, serie, numero);
                return ResultadoDeclaracion.rechazado("TICKET_NO_ENCONTRADO",
                        "No se encontró el ticket de comunicación del comprobante " + serie + "-" + numero);
            }
            root.put("ticket", ticket);

            String requestJson = objectMapper.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[APISUNAT ACL] Respuesta baja HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonNode resNode = objectMapper.readTree(response.body());
                String msg = resNode.path("message").asText("Comprobante dado de baja con éxito");
                String hash = resNode.path("payload").path("hash").asText(resNode.path("hash").asText(null));
                return ResultadoDeclaracion.anulado("0", msg, hash);
            } else {
                String errorMsg = extractMessageFromJson(response.body());
                log.warn("[APISUNAT ACL] Error en anulación APISUNAT (HTTP {}): {}", response.statusCode(), errorMsg);
                return ResultadoDeclaracion.rechazado(String.valueOf(response.statusCode()), errorMsg);
            }
        } catch (Exception e) {
            log.error("[APISUNAT ACL] Fallo al anular comprobante en APISUNAT: {}", e.getMessage());
            throw new ProviderUnavailableException("Error conectando con APISUNAT para anulación: " + e.getMessage());
        }
    }

    /**
     * Obtiene el ticket de comunicacion de un comprobante.
     *
     * <p>La autoridad fiscal exige ese ticket en la solicitud de anulacion:
     * es lo que acredita que el documento existe y ha sido emitido. Sin el,
     * la solicitud se rechaza por un motivo que no dice nada del problema
     * real.
     */
    private String obtenerTicketDeComunicacion(String docTipo, String serie, int numero) {
        try {
            String targetUrl = baseUrl.endsWith("/")
                    ? baseUrl + "api/v3/tickets"
                    : baseUrl + "/api/v3/tickets";
            String consulta = "?documento=" + java.net.URLEncoder.encode(docTipo, java.nio.charset.StandardCharsets.UTF_8)
                    + "&serie=" + java.net.URLEncoder.encode(serie, java.nio.charset.StandardCharsets.UTF_8)
                    + "&numero=" + numero;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + consulta))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.warn("[APISUNAT ACL] El endpoint de tickets respondió HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode payload = root.path("payload");
            String ticket = payload.hasNonNull("ticket")
                    ? payload.get("ticket").asText()
                    : root.path("ticket").asText(root.path("hash").asText(null));

            if (ticket == null || ticket.isBlank()) {
                log.warn("[APISUNAT ACL] La respuesta de tickets no trae ticket: {}", response.body());
            }
            return ticket;
        } catch (Exception e) {
            log.warn("[APISUNAT ACL] No se pudo obtener el ticket de comunicación: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero) {
        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "api/v3/status" : baseUrl + "/api/v3/status";
            String docTipo = tipo != null ? tipo.toLowerCase() : "factura";

            ObjectNode root = objectMapper.createObjectNode();
            root.put("documento", docTipo);
            root.put("serie", serie);
            root.put("numero", numero);

            String requestJson = objectMapper.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseApiSunatSuccessResponse(response.body());
        } catch (Exception e) {
            log.error("[APISUNAT ACL] Error al consultar estado de comprobante: {}", e.getMessage());
            throw new ProviderUnavailableException("Error consultando estado en APISUNAT: " + e.getMessage());
        }
    }

    private String buildApiSunatJsonPayload(ComprobanteParaDeclarar c) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        String documento = switch (c.getTipo()) {
            case FACTURA -> "factura";
            case BOLETA -> "boleta";
            case NOTA_CREDITO -> "nota_credito";
            case NOTA_DEBITO -> "nota_debito";
        };

        String moneda = c.getMoneda() == Moneda.USD ? "USD" : "PEN";
        String fechaStr = c.getFechaEmision() != null
                ? c.getFechaEmision().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String horaStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        root.put("documento", documento);
        root.put("serie", c.getSerie());
        root.put("numero", c.getNumero());
        root.put("fecha_de_emision", fechaStr);
        root.put("hora_de_emision", horaStr);
        root.put("moneda", moneda);
        root.put("tipo_operacion", "0101");
        root.put("cliente_tipo_de_documento", c.getClienteTipoDoc());
        root.put("cliente_numero_de_documento", c.getClienteNumDoc());
        root.put("cliente_denominacion", c.getClienteDenominacion() != null ? c.getClienteDenominacion() : "");
        root.put("cliente_direccion", c.getClienteDireccion() != null ? c.getClienteDireccion() : "");

        ArrayNode itemsArray = root.putArray("items");
        for (ItemComprobante item : c.getItems()) {
            ObjectNode itemNode = itemsArray.addObject();
            itemNode.put("unidad_de_medida", item.unidadMedida() != null ? item.unidadMedida() : "NIU");
            itemNode.put("descripcion", item.descripcion());
            itemNode.put("cantidad", item.cantidad().setScale(2, RoundingMode.HALF_UP).toPlainString());
            itemNode.put("valor_unitario", item.valorUnitario().setScale(6, RoundingMode.HALF_UP).toPlainString());
            itemNode.put("porcentaje_igv", "18");
            itemNode.put("codigo_tipo_afectacion_igv", "10");
            itemNode.put("nombre_tributo", "IGV");
        }

        root.put("total", c.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString());

        return objectMapper.writeValueAsString(root);
    }

    private ResultadoDeclaracion parseApiSunatSuccessResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payloadNode = root.path("payload");

            String estado = payloadNode.hasNonNull("estado")
                    ? payloadNode.get("estado").asText()
                    : root.path("estado").asText("ACEPTADO");

            String message = root.path("message").asText("Comprobante procesado por APISUNAT");

            String hash = payloadNode.hasNonNull("hash")
                    ? payloadNode.get("hash").asText()
                    : root.path("hash").asText(null);

            String xml = payloadNode.hasNonNull("xml")
                    ? payloadNode.get("xml").asText()
                    : root.path("xml").asText(null);

            String cdr = payloadNode.hasNonNull("cdr")
                    ? payloadNode.get("cdr").asText()
                    : root.path("cdr").asText(null);

            String pdf = null;
            if (payloadNode.hasNonNull("pdf")) {
                JsonNode pdfNode = payloadNode.get("pdf");
                if (pdfNode.isTextual()) {
                    pdf = pdfNode.asText();
                } else {
                    pdf = pdfNode.path("a4").asText(pdfNode.path("ticket").asText(null));
                }
            } else if (root.hasNonNull("pdf")) {
                pdf = root.path("pdf").asText(null);
            }

            // El codigo de respuesta es el que INFORMO el proveedor: antes
            // se sustituia por un literal, de modo que el diagnostico que
            // llegaba al taller no era el de SUNAT.
            String codigo = payloadNode.hasNonNull("codigoRespuesta")
                    ? payloadNode.get("codigoRespuesta").asText()
                    : root.path("codigoRespuesta").asText(root.path("codigo").asText("0"));

            // El estado se traduce al conjunto explicito. OBSERVADO es un
            // estado DISTINTO de RECHAZADO: un comprobante observado ha sido
            // aceptado con una observacion, y no se puede volver a emitir.
            com.erp.integrationhub.domain.model.EstadoComprobante estadoReal =
                    com.erp.integrationhub.domain.model.EstadoComprobante.desdeProveedor(estado);

            return ResultadoDeclaracion.conEstado(estadoReal, codigo, message, pdf, xml, cdr, hash);
        } catch (Exception e) {
            log.warn("[APISUNAT ACL] Error al parsear respuesta JSON de APISUNAT: {}", e.getMessage());
            return ResultadoDeclaracion.rechazado("500", "Respuesta no estructurada de APISUNAT: " + json);
        }
    }

    private String extractMessageFromJson(String json) {
        if (json == null || json.isBlank()) return "Sin detalle de error";
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {}
        return json;
    }
}
