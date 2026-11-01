package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
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
 * External adapter querying vehicle license plates against SUNARP via ApiPeru.
 */
public class SunarpVehicleValidationAdapter implements VehicleValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(SunarpVehicleValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SunarpVehicleValidationAdapter(String baseUrl, String token, boolean sandboxMode) {
        this(baseUrl, token, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build(), new ObjectMapper());
    }

    public SunarpVehicleValidationAdapter(String baseUrl, String token, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://apiperu.dev/api";
        this.token = token != null ? token.trim() : "";
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
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
            String requestPayload = String.format("{\"placa\":\"%s\"}", sanitizedPlate);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestPayload));

            if (!token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            log.info("[SUNARP ACL] Upstream HTTP response status: {}", response.statusCode());

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                return parseSuccessfulResponse(sanitizedPlate, response.body());
            }

            if (statusCode == 404) {
                log.info("[SUNARP ACL] Plate {} not found in vehicle registry (HTTP 404)", sanitizedPlate);
                return QueryPlateResult.invalid(sanitizedPlate);
            }

            if (statusCode == 429) {
                log.error("[SUNARP ACL] Rate limit exceeded querying SUNARP for plate {}", sanitizedPlate);
                throw new ProviderUnavailableException("SUNARP/ApiPeru rate limit exceeded (HTTP 429)");
            }

            if (statusCode >= 500) {
                log.error("[SUNARP ACL] Upstream server error {} querying SUNARP for plate {}", statusCode, sanitizedPlate);
                throw new ProviderUnavailableException("SUNARP/ApiPeru server error (HTTP " + statusCode + ")");
            }

            log.error("[SUNARP ACL] Unexpected HTTP status {} querying SUNARP for plate {}", statusCode, sanitizedPlate);
            throw new ProviderUnavailableException("SUNARP/ApiPeru unexpected response status: " + statusCode);

        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[SUNARP ACL] Thread interrupted while querying SUNARP for plate {}: {}", sanitizedPlate, e.getMessage());
            throw new ProviderUnavailableException("Interrupted querying SUNARP: " + e.getMessage());
        } catch (Exception e) {
            log.error("[SUNARP ACL] Network or communication failure querying SUNARP for plate {}: {}", sanitizedPlate, e.getMessage());
            throw new ProviderUnavailableException("Communication failure with SUNARP/ApiPeru: " + e.getMessage());
        }
    }

    private QueryPlateResult parseSuccessfulResponse(String plate, String body) {
        if (body == null || body.isBlank()) {
            return QueryPlateResult.invalid(plate);
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            if (root.has("success") && !root.get("success").asBoolean(true)) {
                log.warn("[SUNARP ACL] Upstream indicated failure for plate {}: {}", plate,
                        extractNodeField(root, "message", "error"));
                return QueryPlateResult.invalid(plate);
            }

            JsonNode targetNode = root.hasNonNull("data") ? root.get("data") : root;

            String brand = extractField(targetNode, root, "marca", "brand");

            if (brand != null && !brand.isBlank()) {
                String model = extractField(targetNode, root, "modelo", "model");
                String vin = extractField(targetNode, root, "vin", "serie", "vinNumber");
                String engineNumber = extractField(targetNode, root, "numero_motor", "motor", "engineNumber");
                String manufactureYear = extractField(targetNode, root, "anio_fabricacion", "anio", "year");
                String color = extractField(targetNode, root, "color");
                String fuelType = extractField(targetNode, root, "combustible", "fuelType");

                return new QueryPlateResult(
                        plate,
                        brand,
                        model,
                        vin,
                        engineNumber,
                        manufactureYear,
                        color,
                        fuelType,
                        true
                );
            }

            return QueryPlateResult.invalid(plate);
        } catch (Exception e) {
            log.error("[SUNARP ACL] Error parsing JSON response for plate {}: {}", plate, e.getMessage());
            return QueryPlateResult.invalid(plate);
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
