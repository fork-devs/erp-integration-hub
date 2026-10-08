package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.ComprobanteParaDeclarar;
import com.erp.integrationhub.domain.model.ComprobanteTipo;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

public class NubefactElectronicInvoicingAdapter implements EmisorTributarioPort {

    private static final Logger log = LoggerFactory.getLogger(NubefactElectronicInvoicingAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;
    private final HttpClient httpClient;

    public NubefactElectronicInvoicingAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://api.nubefact.com/api/v1";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ResultadoDeclaracion emitirComprobante(ComprobanteParaDeclarar comprobante) {
        log.info("[NUBEFACT ACL] Declarando {} {}-{} a SUNAT para cliente {} ({})",
                comprobante.getTipo(), comprobante.getSerie(), comprobante.getNumero(),
                comprobante.getClienteDenominacion(), comprobante.getClienteNumDoc());

        // Si estamos en modo sandbox con token simulado (sin credencial real), devolvemos datos generados
        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            String hash = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String cdrUrl = "https://cdn.nubefact.com/cdr/R-" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".zip";
            String pdfUrl = "https://cdn.nubefact.com/pdf/" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".pdf";
            String xmlUrl = "https://cdn.nubefact.com/xml/" + comprobante.getSerie() + "-" + comprobante.getNumero() + ".xml";

            return ResultadoDeclaracion.aceptado(
                    "0",
                    "La Factura numero " + comprobante.getSerie() + "-" + comprobante.getNumero() + " ha sido ACEPTADA por SUNAT",
                    pdfUrl,
                    xmlUrl,
                    cdrUrl,
                    hash
            );
        }

        // Llamada HTTP Real al API de Nubefact / PSE SUNAT
        try {
            String requestJson = buildNubefactJsonPayload(comprobante);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[NUBEFACT ACL] Respuesta HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return parseNubefactSuccessResponse(response.body(), comprobante);
            } else {
                String errorMsg = extractJsonString(response.body(), "errors");
                if (errorMsg == null) errorMsg = response.body();
                log.warn("[NUBEFACT ACL] Error devuelto por SUNAT/PSE: {}", errorMsg);
                return ResultadoDeclaracion.rechazado(String.valueOf(response.statusCode()), errorMsg);
            }

        } catch (Exception e) {
            log.error("[NUBEFACT ACL] Fallo de conexion con API Nubefact: {}", e.getMessage());
            throw new ProviderUnavailableException("Error conectando con Nubefact / SUNAT PSE: " + e.getMessage());
        }
    }

    @Override
    public ResultadoDeclaracion consultarEstadoComprobante(String tipo, String serie, int numero) {
        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            return ResultadoDeclaracion.aceptado("0", "Comprobante emitido y registrado en SUNAT", null, null, null, null);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/consultar"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"operacion\":\"consultar_comprobante\",\"tipo_de_comprobante\":1,\"serie\":\"%s\",\"numero\":%d}", serie, numero)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseNubefactSuccessResponse(response.body(), null);
        } catch (Exception e) {
            log.error("[NUBEFACT ACL] Error al consultar comprobante: {}", e.getMessage());
            throw new ProviderUnavailableException("Error consultando estado en Nubefact: " + e.getMessage());
        }
    }

    private String buildNubefactJsonPayload(ComprobanteParaDeclarar c) {
        int tipoNubefact = switch (c.getTipo()) {
            case FACTURA -> 1;
            case BOLETA -> 2;
            case NOTA_CREDITO -> 3;
            case NOTA_DEBITO -> 4;
        };

        int monedaNubefact = c.getMoneda() == Moneda.USD ? 2 : 1;
        String fechaStr = c.getFechaEmision() != null
                ? c.getFechaEmision().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                : java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        String itemsJson = c.getItems().stream()
                .map(this::buildItemJson)
                .collect(Collectors.joining(","));

        return String.format("""
                {
                    "operacion": "generar_comprobante",
                    "tipo_de_comprobante": %d,
                    "serie": "%s",
                    "numero": %d,
                    "sunat_transaction": 1,
                    "cliente_tipo_de_documento": "%s",
                    "cliente_numero_de_documento": "%s",
                    "cliente_denominacion": "%s",
                    "cliente_direccion": "%s",
                    "cliente_email": "%s",
                    "fecha_de_emision": "%s",
                    "moneda": %d,
                    "porcentaje_de_igv": 18.0,
                    "total_gravada": %s,
                    "total_igv": %s,
                    "total": %s,
                    "enviar_automaticamente_a_la_sunat": true,
                    "enviar_automaticamente_al_cliente": true,
                    "items": [%s]
                }
                """,
                tipoNubefact,
                c.getSerie(),
                c.getNumero(),
                c.getClienteTipoDoc(),
                c.getClienteNumDoc(),
                escapeJson(c.getClienteDenominacion()),
                c.getClienteDireccion() != null ? escapeJson(c.getClienteDireccion()) : "",
                c.getClienteEmail() != null ? escapeJson(c.getClienteEmail()) : "",
                fechaStr,
                monedaNubefact,
                c.getTotalGravada().toPlainString(),
                c.getTotalIgv().toPlainString(),
                c.getTotal().toPlainString(),
                itemsJson
        );
    }

    private String buildItemJson(ItemComprobante i) {
        return String.format("""
                {
                    "unidad_de_medida": "%s",
                    "descripcion": "%s",
                    "cantidad": %s,
                    "valor_unitario": %s,
                    "precio_unitario": %s,
                    "subtotal": %s,
                    "tipo_de_igv": 1,
                    "igv": %s,
                    "total": %s,
                    "anticipo_regularizacion": false
                }
                """,
                i.unidadMedida(),
                escapeJson(i.descripcion()),
                i.cantidad().toPlainString(),
                i.valorUnitario().toPlainString(),
                i.precioUnitario().toPlainString(),
                i.valorUnitario().multiply(i.cantidad()).toPlainString(),
                i.igv().toPlainString(),
                i.total().toPlainString()
        );
    }

    private ResultadoDeclaracion parseNubefactSuccessResponse(String json, ComprobanteParaDeclarar c) {
        boolean aceptada = json.contains("\"aceptada_por_sunat\":true") || json.contains("\"aceptada_por_sunat\": true");
        String desc = extractJsonString(json, "sunat_description");
        if (desc == null) desc = "Comprobante procesado exitosamente por SUNAT";
        String code = extractJsonString(json, "sunat_responsecode");
        if (code == null) code = "0";

        String pdfUrl = extractJsonString(json, "enlace_del_pdf");
        String xmlUrl = extractJsonString(json, "enlace_del_xml");
        String cdrUrl = extractJsonString(json, "enlace_del_cdr");
        String hash = extractJsonString(json, "codigo_hash");

        return aceptada
                ? ResultadoDeclaracion.aceptado(code, desc, pdfUrl, xmlUrl, cdrUrl, hash)
                : ResultadoDeclaracion.rechazado(code, desc);
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

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
