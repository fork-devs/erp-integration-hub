package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.InvoiceItem;
import com.erp.integrationhub.domain.model.InvoiceStatus;
import com.erp.integrationhub.domain.model.InvoiceToDeclare;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
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

/**
 * External adapter communicating with SUNAT via the APISUNAT (v3) electronic billing API.
 */
public class ApiSunatElectronicInvoicingAdapter implements TaxAuthorityEmitterPort {

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
    public DeclarationResult emitInvoice(InvoiceToDeclare invoice) {
        log.info("[APISUNAT ACL] Declaring {} {}-{} to SUNAT for customer {} ({})",
                invoice.getType(), invoice.getSeries(), invoice.getNumber(),
                invoice.getCustomerLegalName(), invoice.getCustomerDocNumber());

        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "api/v3/documents" : baseUrl + "/api/v3/documents";
            String requestJson = buildApiSunatJsonPayload(invoice);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[APISUNAT ACL] HTTP response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return parseApiSunatSuccessResponse(response.body());
            } else {
                String errorMsg = extractMessageFromJson(response.body());
                log.warn("[APISUNAT ACL] Error returned by APISUNAT/SUNAT (HTTP {}): {}", response.statusCode(), errorMsg);
                return DeclarationResult.rejected(String.valueOf(response.statusCode()), errorMsg);
            }

        } catch (Exception e) {
            log.error("[APISUNAT ACL] Connection failure with APISUNAT: {}", e.getMessage());
            throw new ProviderUnavailableException("Error connecting to APISUNAT: " + e.getMessage());
        }
    }

    @Override
    public DeclarationResult voidInvoice(String type, String series, int number, String reason) {
        log.info("[APISUNAT ACL] Requesting voiding of {} {}-{} for reason: {}", type, series, number, reason);

        try {
            boolean isBoleta = type != null && type.toLowerCase().contains("boleta");
            String endpoint = isBoleta ? "api/v3/daily-summary" : "api/v3/voided";
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + endpoint : baseUrl + "/" + endpoint;

            ObjectNode root = objectMapper.createObjectNode();
            String docType = isBoleta ? "boleta" : (type != null ? type.toLowerCase().replace(" ", "_") : "factura");
            root.put("documento", docType);
            root.put("serie", series);
            root.put("numero", number);
            root.put("motivo", reason != null ? reason : "Anulación de comprobante");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[APISUNAT ACL] Voiding HTTP response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200 || response.statusCode() == 202) {
                return DeclarationResult.withStatus(InvoiceStatus.VOIDED, "0", "Voucher voided successfully", null, null, null, null);
            } else {
                String errorMsg = extractMessageFromJson(response.body());
                log.warn("[APISUNAT ACL] Failed voiding document in APISUNAT (HTTP {}): {}", response.statusCode(), errorMsg);
                return DeclarationResult.rejected(String.valueOf(response.statusCode()), errorMsg);
            }

        } catch (Exception e) {
            log.error("[APISUNAT ACL] Error communicating with APISUNAT voiding endpoint: {}", e.getMessage());
            throw new ProviderUnavailableException("Error communicating with APISUNAT voiding: " + e.getMessage());
        }
    }

    @Override
    public DeclarationResult queryInvoiceStatus(String type, String series, int number) {
        log.info("[APISUNAT ACL] Querying status for {} {}-{}", type, series, number);

        try {
            String targetUrl = String.format("%s/api/v3/status?documento=%s&serie=%s&numero=%d",
                    baseUrl.replaceAll("/$", ""),
                    type != null ? type.toLowerCase() : "factura",
                    series,
                    number
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[APISUNAT ACL] Status query HTTP response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                return parseApiSunatSuccessResponse(response.body());
            } else {
                return DeclarationResult.rejected(String.valueOf(response.statusCode()), "Status query returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[APISUNAT ACL] Error querying document status: {}", e.getMessage());
            throw new ProviderUnavailableException("Error querying status in APISUNAT: " + e.getMessage());
        }
    }

    private String buildApiSunatJsonPayload(InvoiceToDeclare c) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        String document = switch (c.getType()) {
            case FACTURA -> "factura";
            case BOLETA -> "boleta";
            case NOTA_CREDITO -> "nota_credito";
            case NOTA_DEBITO -> "nota_debito";
        };

        String currency = c.getCurrency() != null ? c.getCurrency().getCode() : "PEN";
        String issueDateStr = c.getIssueDate() != null
                ? c.getIssueDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                : java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String issueTimeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        root.put("documento", document);
        root.put("serie", c.getSeries());
        root.put("numero", c.getNumber());
        root.put("fecha_de_emision", issueDateStr);
        root.put("hora_de_emision", issueTimeStr);
        root.put("moneda", currency);
        root.put("tipo_operacion", "0101");
        root.put("cliente_tipo_de_documento", c.getCustomerDocType());
        root.put("cliente_numero_de_documento", c.getCustomerDocNumber());
        root.put("cliente_denominacion", c.getCustomerLegalName() != null ? c.getCustomerLegalName() : "");
        root.put("cliente_direccion", c.getCustomerAddress() != null ? c.getCustomerAddress() : "");

        ArrayNode itemsArray = root.putArray("items");
        for (InvoiceItem item : c.getItems()) {
            ObjectNode itemNode = itemsArray.addObject();
            itemNode.put("unidad_de_medida", item.unitOfMeasure() != null ? item.unitOfMeasure() : "NIU");
            itemNode.put("descripcion", item.description());
            itemNode.put("cantidad", item.quantity().setScale(2, RoundingMode.HALF_UP).toPlainString());
            itemNode.put("valor_unitario", item.unitValue().setScale(6, RoundingMode.HALF_UP).toPlainString());
            itemNode.put("porcentaje_igv", "18");
            itemNode.put("codigo_tipo_afectacion_igv", "10");
            itemNode.put("nombre_tributo", "IGV");
        }

        root.put("total", c.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString());

        return objectMapper.writeValueAsString(root);
    }

    private DeclarationResult parseApiSunatSuccessResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payloadNode = root.path("payload");

            String statusStr = payloadNode.hasNonNull("estado")
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

            String code = payloadNode.hasNonNull("codigoRespuesta")
                    ? payloadNode.get("codigoRespuesta").asText()
                    : root.path("codigoRespuesta").asText(root.path("codigo").asText("0"));

            InvoiceStatus realStatus = InvoiceStatus.fromProvider(statusStr);
            return DeclarationResult.withStatus(realStatus, code, message, pdf, xml, cdr, hash);
        } catch (Exception e) {
            log.warn("[APISUNAT ACL] Error parsing JSON response from APISUNAT: {}", e.getMessage());
            return DeclarationResult.rejected("500", "Unstructured response from APISUNAT: " + json);
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
