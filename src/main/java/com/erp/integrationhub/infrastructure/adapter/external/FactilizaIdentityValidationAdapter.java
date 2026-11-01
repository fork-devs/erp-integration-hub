package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryPersonalIdentityResult;
import com.erp.integrationhub.domain.port.PersonalIdentityValidatorPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * External adapter for querying Peruvian personal identity documents (DNI / Carnet de Extranjería)
 * via Factiliza REST API v1.
 */
public class FactilizaIdentityValidationAdapter implements PersonalIdentityValidatorPort {

    private static final Logger log = LoggerFactory.getLogger(FactilizaIdentityValidationAdapter.class);

    private final String baseUrl;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FactilizaIdentityValidationAdapter(String baseUrl, String token) {
        this(baseUrl, token, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .build(), new ObjectMapper());
    }

    public FactilizaIdentityValidationAdapter(String baseUrl, String token, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim().replaceAll("/+$", "") : "https://api.factiliza.com/pe/v1";
        this.token = token != null ? token.trim() : "";
        this.httpClient = httpClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public QueryPersonalIdentityResult queryDni(String dni) {
        log.info("[FACTILIZA ACL] Querying RENIEC identity registry for DNI: {}", dni);

        if (dni == null || !dni.matches("^\\d{8}$")) {
            log.warn("[FACTILIZA ACL] Invalid DNI format: {}", dni);
            return QueryPersonalIdentityResult.invalid("DNI", dni);
        }

        String targetUrl = baseUrl + "/dni/info/" + dni;
        return executeQuery("DNI", dni, targetUrl);
    }

    @Override
    public QueryPersonalIdentityResult queryCee(String cee) {
        log.info("[FACTILIZA ACL] Querying Migraciones foreign registry for CE: {}", cee);

        if (cee == null || cee.trim().isBlank()) {
            log.warn("[FACTILIZA ACL] Invalid CE format: {}", cee);
            return QueryPersonalIdentityResult.invalid("CE", cee);
        }

        String targetUrl = baseUrl + "/cee/info/" + cee.trim();
        return executeQuery("CE", cee.trim(), targetUrl);
    }

    private QueryPersonalIdentityResult executeQuery(String docType, String docNumber, String targetUrl) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();

            if (!token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            log.info("[FACTILIZA ACL] Upstream HTTP response status: {} for {} {}", statusCode, docType, docNumber);

            if (statusCode == 200) {
                return parseSuccessfulResponse(docType, docNumber, response.body());
            }

            if (statusCode == 404) {
                log.info("[FACTILIZA ACL] {} {} not found in official registry (HTTP 404)", docType, docNumber);
                return QueryPersonalIdentityResult.invalid(docType, docNumber);
            }

            if (statusCode == 429) {
                log.error("[FACTILIZA ACL] Rate limit exceeded querying Factiliza for {} {}", docType, docNumber);
                throw new ProviderUnavailableException("Factiliza rate limit exceeded (HTTP 429)");
            }

            if (statusCode >= 500) {
                log.error("[FACTILIZA ACL] Upstream server error {} from Factiliza for {} {}", statusCode, docType, docNumber);
                throw new ProviderUnavailableException("Factiliza server error (HTTP " + statusCode + ")");
            }

            log.error("[FACTILIZA ACL] Unexpected HTTP status {} from Factiliza for {} {}", statusCode, docType, docNumber);
            throw new ProviderUnavailableException("Factiliza returned unexpected status: " + statusCode);

        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[FACTILIZA ACL] Thread interrupted querying Factiliza for {} {}: {}", docType, docNumber, e.getMessage());
            throw new ProviderUnavailableException("Interrupted querying Factiliza: " + e.getMessage());
        } catch (Exception e) {
            log.error("[FACTILIZA ACL] Network or communication failure querying Factiliza for {} {}: {}", docType, docNumber, e.getMessage());
            throw new ProviderUnavailableException("Communication failure with Factiliza: " + e.getMessage());
        }
    }

    private QueryPersonalIdentityResult parseSuccessfulResponse(String docType, String docNumber, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.has("data") && root.get("data").isObject() ? root.get("data") : root;

            String nombres = extractField(data, "nombres", "nombre", "firstName");
            String apellidoPaterno = extractField(data, "apellido_paterno", "apellidoPaterno", "paternal_surname");
            String apellidoMaterno = extractField(data, "apellido_materno", "apellidoMaterno", "maternal_surname");
            String nombreCompleto = extractField(data, "nombre_completo", "nombreCompleto", "fullName");
            String codigoVerificacion = extractField(data, "codigo_verificacion", "codigoVerificacion");

            if (nombreCompleto == null || nombreCompleto.isBlank()) {
                nombreCompleto = Stream.of(nombres, apellidoPaterno, apellidoMaterno)
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(" "))
                        .trim();
            }

            return new QueryPersonalIdentityResult(
                    docType,
                    docNumber,
                    nombres,
                    apellidoPaterno,
                    apellidoMaterno,
                    nombreCompleto,
                    codigoVerificacion,
                    true
            );
        } catch (Exception e) {
            log.error("[FACTILIZA ACL] Failed to parse Factiliza JSON response for {} {}: {}", docType, docNumber, e.getMessage());
            throw new ProviderUnavailableException("Failed to parse Factiliza response: " + e.getMessage());
        }
    }

    private String extractField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.hasNonNull(fieldName)) {
                String val = node.get(fieldName).asText();
                if (val != null && !val.isBlank()) {
                    return val.trim();
                }
            }
        }
        return null;
    }
}
