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

public class SunatReniecValidationAdapter implements ValidadorIdentidadPort {

    private static final Logger log = LoggerFactory.getLogger(SunatReniecValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;
    private final HttpClient httpClient;

    public SunatReniecValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://apiperu.dev/api";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @Override
    public ConsultaRucResult consultarRuc(String ruc) {
        log.info("[SUNAT/RENIEC ACL] Consultando padrón fiscal para RUC: {}", ruc);

        if (ruc == null || !ruc.matches("^(10|20|15|17)\\d{9}$")) {
            return ConsultaRucResult.invalido(ruc);
        }

        // Modo sandbox si la credencial es de prueba/mock
        if (sandboxMode && (token.contains("mock") || token.contains("sandbox") || token.isBlank())) {
            String razonSocial = ruc.startsWith("20") ? "SERVICIOS AUTOMOTRICES LIMA S.A.C." : "JUAN CARLOS PEREZ GOMEZ";
            return new ConsultaRucResult(
                    ruc,
                    razonSocial,
                    "ACTIVO",
                    "HABIDO",
                    "AV. JAVIER PRADO ESTE 1234, SAN ISIDRO",
                    "LIMA",
                    "LIMA",
                    "SAN ISIDRO",
                    "150131",
                    true
            );
        }

        // Llamada HTTP Real a API de validación SUNAT / RENIEC
        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "ruc" : baseUrl + "/ruc";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"ruc\":\"%s\"}", ruc)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[SUNAT/RENIEC ACL] Respuesta HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                String razonSocial = extractJsonString(body, "nombre_o_razon_social");
                if (razonSocial == null) razonSocial = extractJsonString(body, "razon_social");
                String estado = extractJsonString(body, "estado");
                String condicion = extractJsonString(body, "condicion");
                String direccion = extractJsonString(body, "direccion_completa");
                if (direccion == null) direccion = extractJsonString(body, "direccion");
                String departamento = extractJsonString(body, "departamento");
                String provincia = extractJsonString(body, "provincia");
                String distrito = extractJsonString(body, "distrito");
                String ubigeo = extractJsonString(body, "ubigeo_sunat");
                if (ubigeo == null) ubigeo = extractJsonString(body, "ubigeo");

                if (razonSocial != null && !razonSocial.isBlank()) {
                    return new ConsultaRucResult(
                            ruc,
                            razonSocial,
                            estado != null ? estado : "ACTIVO",
                            condicion != null ? condicion : "HABIDO",
                            direccion,
                            departamento,
                            provincia,
                            distrito,
                            ubigeo,
                            true
                    );
                }
            }

            return ConsultaRucResult.invalido(ruc);

        } catch (Exception e) {
            log.error("[SUNAT/RENIEC ACL] Error al consultar API de RUC {}: {}", ruc, e.getMessage());
            return ConsultaRucResult.invalido(ruc);
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
}
