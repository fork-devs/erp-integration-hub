package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SunarpVehicleValidationAdapter implements ValidadorVehicularPort {

    private static final Logger log = LoggerFactory.getLogger(SunarpVehicleValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final boolean sandboxMode;
    private final HttpClient httpClient;

    public SunarpVehicleValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://apiperu.dev/api";
        this.token = token != null ? token.trim() : "";
        this.sandboxMode = sandboxMode;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @Override
    public ConsultaPlacaResult consultarPlaca(String placa) {
        log.info("[SUNARP ACL] Consultando padrón vehicular para Placa: {}", placa);

        if (placa == null || placa.trim().length() < 5) {
            return ConsultaPlacaResult.invalido(placa);
        }

        String sanitizedPlaca = placa.trim().toUpperCase().replace("-", "");

        // Modo sandbox si la credencial es de prueba/mock
        // Llamada HTTP Real al API de SUNARP
        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "placa" : baseUrl + "/placa";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"placa\":\"%s\"}", sanitizedPlaca)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[SUNARP ACL] Respuesta HTTP {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                String marca = extractJsonString(body, "marca");
                String modelo = extractJsonString(body, "modelo");
                String vin = extractJsonString(body, "vin");
                if (vin == null) vin = extractJsonString(body, "serie");
                String numeroMotor = extractJsonString(body, "numero_motor");
                if (numeroMotor == null) numeroMotor = extractJsonString(body, "motor");
                String anio = extractJsonString(body, "anio_fabricacion");
                if (anio == null) anio = extractJsonString(body, "anio");
                String color = extractJsonString(body, "color");
                String combustible = extractJsonString(body, "combustible");

                if (marca != null && !marca.isBlank()) {
                    return new ConsultaPlacaResult(
                            sanitizedPlaca,
                            marca,
                            modelo != null ? modelo : "DESCONOCIDO",
                            vin,
                            numeroMotor,
                            anio,
                            color,
                            combustible,
                            true
                    );
                }
            }

            return ConsultaPlacaResult.invalido(sanitizedPlaca);

        } catch (Exception e) {
            log.error("[SUNARP ACL] Error al consultar API de Placa {}: {}", sanitizedPlaca, e.getMessage());
            return ConsultaPlacaResult.invalido(sanitizedPlaca);
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
