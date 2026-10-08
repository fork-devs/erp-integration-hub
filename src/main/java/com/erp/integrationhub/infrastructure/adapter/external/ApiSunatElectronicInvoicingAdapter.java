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
        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            String hash = "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=";
            String cleanDoc = comprobante.getClienteNumDoc() != null ? comprobante.getClienteNumDoc() : "20123456789";
            String code = comprobante.getTipo().name().startsWith("F") ? "01" : "03";
            String cdrUrl = String.format("https://apisunat.pe/R-%s-%s-%s-%d.xml", cleanDoc, code, comprobante.getSerie(), comprobante.getNumero());
            String xmlUrl = String.format("https://apisunat.pe/%s-%s-%s-%d.xml", cleanDoc, code, comprobante.getSerie(), comprobante.getNumero());
            String pdfUrl = String.format("https://apisunat.pe/pdf/a4/%s-%s-%s-%d", cleanDoc, code, comprobante.getSerie(), comprobante.getNumero());

            return ResultadoDeclaracion.aceptado(
                    "0",
                    "El comprobante fue enviado y aceptado por SUNAT.",
                    pdfUrl,
                    xmlUrl,
                    cdrUrl,
                    hash
            );
        }

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

        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            return ResultadoDeclaracion.anulado("0", "El comprobante fue dado de baja exitosamente en SUNAT.", "BAJA-" + UUID.randomUUID().toString().substring(0, 8));
        }

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

    @Override
    public ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero) {
        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            return ResultadoDeclaracion.aceptado("0", "El comprobante fue enviado y aceptado por SUNAT.", null, null, null, null);
        }

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

            if ("ACEPTADO".equalsIgnoreCase(estado)) {
                return ResultadoDeclaracion.aceptado("0", message, pdf, xml, cdr, hash);
            } else if ("PENDIENTE".equalsIgnoreCase(estado)) {
                return ResultadoDeclaracion.pendiente(message);
            } else {
                return ResultadoDeclaracion.rechazado("400", message);
            }
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
