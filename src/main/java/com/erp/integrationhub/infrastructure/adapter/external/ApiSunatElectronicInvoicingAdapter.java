package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
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
import java.util.stream.Collectors;

public class ApiSunatElectronicInvoicingAdapter implements EmisorTributarioPort {

    private static final Logger log = LoggerFactory.getLogger(ApiSunatElectronicInvoicingAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;
    private final HttpClient httpClient;

    public ApiSunatElectronicInvoicingAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://sandbox.apisunat.pe";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
                String errorMsg = extractJsonString(response.body(), "message");
                if (errorMsg == null) errorMsg = response.body();
                log.warn("[APISUNAT ACL] Error devuelto por APISUNAT/SUNAT: {}", errorMsg);
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

            String docTipo = tipo != null ? tipo.toLowerCase().replace(" ", "_") : "factura";
            String requestJson = isBoleta
                    ? String.format("{\"documento\":\"boleta\",\"serie\":\"%s\",\"numero\":%d,\"motivo\":\"%s\",\"accion\":\"anulacion\"}",
                            serie, numero, escapeJson(motivo))
                    : String.format("{\"documento\":\"%s\",\"serie\":\"%s\",\"numero\":%d,\"motivo\":\"%s\"}",
                            docTipo, serie, numero, escapeJson(motivo));

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
                String msg = extractJsonString(response.body(), "message");
                String hash = extractJsonString(response.body(), "hash");
                return ResultadoDeclaracion.anulado("0", msg != null ? msg : "Comprobante dado de baja con éxito", hash);
            } else {
                String errorMsg = extractJsonString(response.body(), "message");
                if (errorMsg == null) errorMsg = response.body();
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
            String requestJson = String.format("{\"documento\":\"%s\",\"serie\":\"%s\",\"numero\":%d}", docTipo, serie, numero);

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

    private String buildApiSunatJsonPayload(ComprobanteParaDeclarar c) {
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

        String itemsJson = c.getItems().stream()
                .map(this::buildItemJson)
                .collect(Collectors.joining(","));

        return String.format("""
                {
                    "documento": "%s",
                    "serie": "%s",
                    "numero": %d,
                    "fecha_de_emision": "%s",
                    "hora_de_emision": "%s",
                    "moneda": "%s",
                    "tipo_operacion": "0101",
                    "cliente_tipo_de_documento": "%s",
                    "cliente_numero_de_documento": "%s",
                    "cliente_denominacion": "%s",
                    "cliente_direccion": "%s",
                    "items": [%s],
                    "total": "%s"
                }
                """,
                documento,
                c.getSerie(),
                c.getNumero(),
                fechaStr,
                horaStr,
                moneda,
                c.getClienteTipoDoc(),
                c.getClienteNumDoc(),
                escapeJson(c.getClienteDenominacion()),
                c.getClienteDireccion() != null ? escapeJson(c.getClienteDireccion()) : "",
                itemsJson,
                c.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString()
        );
    }

    private String buildItemJson(ItemComprobante i) {
        return String.format("""
                {
                    "unidad_de_medida": "%s",
                    "descripcion": "%s",
                    "cantidad": "%s",
                    "valor_unitario": "%s",
                    "porcentaje_igv": "18",
                    "codigo_tipo_afectacion_igv": "10",
                    "nombre_tributo": "IGV"
                }
                """,
                i.unidadMedida() != null ? i.unidadMedida() : "NIU",
                escapeJson(i.descripcion()),
                i.cantidad().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                i.valorUnitario().setScale(6, RoundingMode.HALF_UP).toPlainString()
        );
    }

    private ResultadoDeclaracion parseApiSunatSuccessResponse(String json) {
        String estado = extractJsonString(json, "estado");
        String message = extractJsonString(json, "message");
        if (message == null) message = "Comprobante procesado por APISUNAT";

        String hash = extractJsonString(json, "hash");
        String xml = extractJsonString(json, "xml");
        String cdr = extractJsonString(json, "cdr");
        String pdf = extractNestedJsonString(json, "pdf", "a4");
        if (pdf == null) pdf = extractNestedJsonString(json, "pdf", "ticket");

        if ("ACEPTADO".equalsIgnoreCase(estado)) {
            return ResultadoDeclaracion.aceptado("0", message, pdf, xml, cdr, hash);
        } else if ("PENDIENTE".equalsIgnoreCase(estado)) {
            return ResultadoDeclaracion.pendiente(message);
        } else {
            return ResultadoDeclaracion.rechazado("400", message);
        }
    }

    private String extractJsonString(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start != -1) {
            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end != -1) return json.substring(start, end);
        }
        return null;
    }

    private String extractNestedJsonString(String json, String parentKey, String childKey) {
        if (json == null) return null;
        int parentStart = json.indexOf("\"" + parentKey + "\"");
        if (parentStart == -1) return null;
        int childStart = json.indexOf("\"" + childKey + "\":\"", parentStart);
        if (childStart == -1) return null;
        childStart += ("\"" + childKey + "\":\"").length();
        int childEnd = json.indexOf("\"", childStart);
        if (childEnd != -1) return json.substring(childStart, childEnd);
        return null;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
