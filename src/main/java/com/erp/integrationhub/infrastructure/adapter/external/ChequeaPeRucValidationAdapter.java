package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * External adapter for querying Peruvian taxpayer (RUC) data via Chequea.pe.
 */
public class ChequeaPeRucValidationAdapter implements IdentityValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(ChequeaPeRucValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChequeaPeRucValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this(baseUrl, token, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build(), new ObjectMapper());
    }

    public ChequeaPeRucValidationAdapter(String baseUrl, String token, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.chequea.pe/v1";
        this.token = token != null ? token.trim() : "";
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public QueryRucResult queryRuc(String ruc) {
        log.info("[CHEQUEA.PE ACL] Querying SUNAT fiscal registry for RUC: {}", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return QueryRucResult.invalid(ruc);
        }

        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "ruc/" + ruc : baseUrl + "/ruc/" + ruc;

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();

            if (!token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
                requestBuilder.header("x-api-key", token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("[CHEQUEA.PE ACL] Upstream HTTP response status: {}", response.statusCode());

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                return parseSuccessfulResponse(ruc, response.body());
            }

            if (statusCode == 404) {
                log.info("[CHEQUEA.PE ACL] RUC {} not found in fiscal registry (HTTP 404)", ruc);
                return QueryRucResult.invalid(ruc);
            }

            if (statusCode == 429) {
                log.error("[CHEQUEA.PE ACL] Rate limit exceeded querying Chequea.pe for RUC {}", ruc);
                throw new ProviderUnavailableException("Chequea.pe rate limit exceeded (HTTP 429)");
            }

            if (statusCode >= 500) {
                log.error("[CHEQUEA.PE ACL] Upstream server error {} from Chequea.pe for RUC {}", statusCode, ruc);
                throw new ProviderUnavailableException("Chequea.pe server error (HTTP " + statusCode + ")");
            }

            log.error("[CHEQUEA.PE ACL] Unexpected HTTP status {} from Chequea.pe for RUC {}", statusCode, ruc);
            throw new ProviderUnavailableException("Chequea.pe returned unexpected status: " + statusCode);

        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[CHEQUEA.PE ACL] Thread interrupted while querying Chequea.pe for RUC {}: {}", ruc, e.getMessage());
            throw new ProviderUnavailableException("Interrupted querying Chequea.pe: " + e.getMessage());
        } catch (Exception e) {
            log.error("[CHEQUEA.PE ACL] Network or communication failure querying Chequea.pe for RUC {}: {}", ruc, e.getMessage());
            throw new ProviderUnavailableException("Communication failure with Chequea.pe: " + e.getMessage());
        }
    }

    private QueryRucResult parseSuccessfulResponse(String ruc, String body) {
        if (body == null || body.isBlank()) {
            return QueryRucResult.invalid(ruc);
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            // Handle possible error response inside HTTP 200 (e.g. {"success": false, "message": "..."})
            if (root.has("success") && !root.get("success").asBoolean(true)) {
                log.warn("[CHEQUEA.PE ACL] Upstream indicated failure for RUC {}: {}", ruc,
                        extractNodeField(root, "message", "error"));
                return QueryRucResult.invalid(ruc);
            }

            JsonNode targetNode = root.hasNonNull("data") ? root.get("data") : root;

            String legalName = extractField(targetNode, root, "razon_social", "nombre_o_razon_social", "razonSocial", "nombre");

            if (legalName != null && !legalName.isBlank()) {
                String status = extractField(targetNode, root, "estado", "estado_contribuyente", "status");
                String condition = extractField(targetNode, root, "condicion", "condicion_domicilio", "condition");
                String address = extractField(targetNode, root, "direccion", "direccion_fiscal", "direccion_completa", "domicilioFiscal", "fiscalAddress");
                String department = extractField(targetNode, root, "departamento", "department");
                String province = extractField(targetNode, root, "provincia", "province");
                String district = extractField(targetNode, root, "distrito", "district");
                String ubigeo = extractField(targetNode, root, "ubigeo", "ubigeo_sunat");

                return new QueryRucResult(
                        ruc,
                        legalName,
                        status != null ? status : "ACTIVO",
                        condition != null ? condition : "HABIDO",
                        address,
                        department,
                        province,
                        district,
                        ubigeo,
                        true
                );
            }

            return QueryRucResult.invalid(ruc);
        } catch (Exception e) {
            log.error("[CHEQUEA.PE ACL] Error parsing JSON response for RUC {}: {}", ruc, e.getMessage());
            return QueryRucResult.invalid(ruc);
        }
    }

    private String extractField(JsonNode node, JsonNode fallbackRoot, String... possibleKeys) {
        String val = extractNodeField(node, possibleKeys);
        if (val == null && fallbackRoot != null && fallbackRoot != node) {
            val = extractNodeField(fallbackRoot, possibleKeys);
        }
        return val;
    }

    private String extractNodeField(JsonNode node, String... possibleKeys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : possibleKeys) {
            if (node.hasNonNull(key)) {
                String text = node.get(key).asText();
                if (text != null && !text.isBlank() && !"null".equalsIgnoreCase(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }
}
