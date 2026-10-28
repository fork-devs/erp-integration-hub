package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * External adapter querying vehicle license plates against SUNARP via ApiPeru.
 */
public class SunarpVehicleValidationAdapter implements VehicleValidatorPort {

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
    public QueryPlateResult queryPlate(String plate) {
        log.info("[SUNARP ACL] Querying vehicle registry for plate: {}", plate);

        if (plate == null || plate.trim().length() < 5) {
            return QueryPlateResult.invalid(plate);
        }

        String sanitizedPlate = plate.trim().toUpperCase().replace("-", "");

        try {
            String targetUrl = baseUrl.endsWith("/") ? baseUrl + "placa" : baseUrl + "/placa";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"placa\":\"%s\"}", sanitizedPlate)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[SUNARP ACL] HTTP response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                String brand = extractJsonString(body, "marca");
                String model = extractJsonString(body, "modelo");
                String vin = extractJsonString(body, "vin");
                if (vin == null) vin = extractJsonString(body, "serie");
                String engineNumber = extractJsonString(body, "numero_motor");
                if (engineNumber == null) engineNumber = extractJsonString(body, "motor");
                String manufactureYear = extractJsonString(body, "anio_fabricacion");
                if (manufactureYear == null) manufactureYear = extractJsonString(body, "anio");
                String color = extractJsonString(body, "color");
                String fuelType = extractJsonString(body, "combustible");

                if (brand != null && !brand.isBlank()) {
                    return new QueryPlateResult(
                            sanitizedPlate,
                            brand,
                            model != null ? model : "UNKNOWN",
                            vin,
                            engineNumber,
                            manufactureYear,
                            color,
                            fuelType,
                            true
                    );
                }
            }

            return QueryPlateResult.invalid(sanitizedPlate);

        } catch (Exception e) {
            log.error("[SUNARP ACL] Error querying plate API for {}: {}", sanitizedPlate, e.getMessage());
            return QueryPlateResult.invalid(sanitizedPlate);
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
