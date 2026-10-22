package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.ConsultaRucResult;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ChequeaPeRucValidationAdapter implements ValidadorIdentidadPort {

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
    public ConsultaRucResult consultarRuc(String ruc) {
        log.info("[CHEQUEA.PE ACL] Consultando padrón fiscal SUNAT para RUC: {}", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return ConsultaRucResult.invalido(ruc);
        }

        // Modo sandbox si la credencial es de prueba o mock
        // Llamada HTTP Real al API de Chequea.pe
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
            log.info("[CHEQUEA.PE ACL] Respuesta HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                String razonSocial = extractJsonField(body, "razon_social", "nombre_o_razon_social", "razonSocial", "nombre");
                String estado = extractJsonField(body, "estado", "estado_contribuyente");
                String condicion = extractJsonField(body, "condicion", "condicion_domicilio");
                String direccion = extractJsonField(body, "direccion", "direccion_fiscal", "direccion_completa", "domicilioFiscal");
                String departamento = extractJsonField(body, "departamento");
                String provincia = extractJsonField(body, "provincia");
                String distrito = extractJsonField(body, "distrito");
                String ubigeo = extractJsonField(body, "ubigeo", "ubigeo_sunat");

                if (razonSocial != null && !razonSocial.isBlank()) {
                    return new ConsultaRucResult(
                            ruc,
                            razonSocial,
                            estado != null ? estado : "ACTIVO",
                            condicion != null ? condicion : "HABIDO",
                            direccion != null ? direccion : "LIMA",
                            departamento != null ? departamento : "LIMA",
                            provincia != null ? provincia : "LIMA",
                            distrito != null ? distrito : "LIMA",
                            ubigeo != null ? ubigeo : "150101",
                            true
                    );
                }
            }

            return ConsultaRucResult.invalido(ruc);

        } catch (Exception e) {
            log.error("[CHEQUEA.PE ACL] Error al consultar API de Chequea.pe para RUC {}: {}", ruc, e.getMessage());
            return ConsultaRucResult.invalido(ruc);
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
