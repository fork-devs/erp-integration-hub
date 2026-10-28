package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
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
    private final boolean sandboxMode;
    private final HttpClient httpClient;

    public ChequeaPeRucValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.chequea.pe/v1";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @Override
    public QueryRucResult queryRuc(String ruc) {
        log.info("[CHEQUEA.PE ACL] Querying SUNAT fiscal registry for RUC: {}", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return QueryRucResult.invalid(ruc);
        }

        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "ruc/" + ruc : baseUrl + "/ruc/" + ruc;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("x-api-key", token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[CHEQUEA.PE ACL] HTTP response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                String legalName = extractJsonField(body, "razon_social", "nombre_o_razon_social", "razonSocial", "nombre");
                String status = extractJsonField(body, "estado", "estado_contribuyente");
                String condition = extractJsonField(body, "condicion", "condicion_domicilio");
                String address = extractJsonField(body, "direccion", "direccion_fiscal", "direccion_completa", "domicilioFiscal");
                String department = extractJsonField(body, "departamento");
                String province = extractJsonField(body, "provincia");
                String district = extractJsonField(body, "distrito");
                String ubigeo = extractJsonField(body, "ubigeo", "ubigeo_sunat");

                if (legalName != null && !legalName.isBlank()) {
                    return new QueryRucResult(
                            ruc,
                            legalName,
                            status != null ? status : "ACTIVO",
                            condition != null ? condition : "HABIDO",
                            address != null ? address : "LIMA",
                            department != null ? department : "LIMA",
                            province != null ? province : "LIMA",
                            district != null ? district : "LIMA",
                            ubigeo != null ? ubigeo : "150101",
                            true
                    );
                }
            }

            return QueryRucResult.invalid(ruc);

        } catch (Exception e) {
            log.error("[CHEQUEA.PE ACL] Error querying Chequea.pe API for RUC {}: {}", ruc, e.getMessage());
            return QueryRucResult.invalid(ruc);
        }
    }

    private String extractJsonField(String json, String... possibleKeys) {
        if (json == null) return null;
        for (String key : possibleKeys) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start != -1) {
                start += pattern.length();
                int end = json.indexOf("\"", start);
                if (end != -1) return json.substring(start, end);
            }
        }
        return null;
    }
}
