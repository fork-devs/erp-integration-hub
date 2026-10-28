package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryRucResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChequeaPeRucValidationAdapterTest {

    private HttpServer server;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Should parse standard flat JSON with spaces and quotes correctly")
    void queryRuc_WhenFlatJsonWithSpaces_ReturnsValidResult() {
        String json = """
                {
                  "ruc": "20601234567",
                  "razon_social": "SERVICIOS AUTOMOTRICES S.A.C.",
                  "estado": "ACTIVO",
                  "condicion": "HABIDO",
                  "direccion": "AV. JAVIER PRADO ESTE 1234",
                  "departamento": "LIMA",
                  "provincia": "LIMA",
                  "distrito": "SAN ISIDRO",
                  "ubigeo": "150131"
                }
                """;
        configureServerResponse("/ruc/20601234567", 200, json);

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        QueryRucResult result = adapter.queryRuc("20601234567");

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("20601234567", result.ruc());
        assertEquals("SERVICIOS AUTOMOTRICES S.A.C.", result.legalName());
        assertEquals("ACTIVO", result.status());
        assertEquals("HABIDO", result.condition());
        assertEquals("AV. JAVIER PRADO ESTE 1234", result.fiscalAddress());
        assertEquals("LIMA", result.department());
        assertEquals("SAN ISIDRO", result.district());
        assertEquals("150131", result.ubigeo());
    }

    @Test
    @DisplayName("Should parse nested data object correctly")
    void queryRuc_WhenNestedDataObject_ReturnsValidResult() {
        String json = """
                {
                  "success": true,
                  "data": {
                    "ruc": "20601234567",
                    "nombre_o_razon_social": "EMPRESA DE TRANSPORTES ANDINA",
                    "estado_contribuyente": "ACTIVO",
                    "condicion_domicilio": "HABIDO",
                    "direccion_completa": "AV. AREQUIPA 500",
                    "departamento": "AREQUIPA",
                    "provincia": "AREQUIPA",
                    "distrito": "YANAHUARA",
                    "ubigeo_sunat": "040126"
                  }
                }
                """;
        configureServerResponse("/ruc/20601234567", 200, json);

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        QueryRucResult result = adapter.queryRuc("20601234567");

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("EMPRESA DE TRANSPORTES ANDINA", result.legalName());
        assertEquals("AREQUIPA", result.department());
        assertEquals("YANAHUARA", result.district());
        assertEquals("040126", result.ubigeo());
    }

    @Test
    @DisplayName("Should return invalid result when RUC is not found (HTTP 404)")
    void queryRuc_WhenNotFound404_ReturnsInvalidResult() {
        configureServerResponse("/ruc/20601234567", 404, "{\"error\": \"RUC not found\"}");

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        QueryRucResult result = adapter.queryRuc("20601234567");

        assertNotNull(result);
        assertFalse(result.valid());
        assertNull(result.legalName());
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException on HTTP 429 rate limit")
    void queryRuc_WhenRateLimited429_ThrowsProviderUnavailableException() {
        configureServerResponse("/ruc/20601234567", 429, "{\"error\": \"Rate limit exceeded\"}");

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryRuc("20601234567"));
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException on HTTP 500 upstream error")
    void queryRuc_WhenServerError500_ThrowsProviderUnavailableException() {
        configureServerResponse("/ruc/20601234567", 500, "{\"error\": \"Internal server error\"}");

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryRuc("20601234567"));
    }

    @Test
    @DisplayName("Should return invalid without network request when RUC format is malformed")
    void queryRuc_WhenMalformedRuc_ReturnsInvalidWithoutRequest() {
        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);

        QueryRucResult result1 = adapter.queryRuc("123");
        assertFalse(result1.valid());

        QueryRucResult result2 = adapter.queryRuc(null);
        assertFalse(result2.valid());

        QueryRucResult result3 = adapter.queryRuc("30123456789");
        assertFalse(result3.valid());
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException when upstream server is unreachable")
    void queryRuc_WhenServerDown_ThrowsProviderUnavailableException() {
        server.stop(0);
        server = null;

        ChequeaPeRucValidationAdapter adapter = new ChequeaPeRucValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryRuc("20601234567"));
    }

    private void configureServerResponse(String path, int statusCode, String responseBody) {
        server.createContext(path, exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }
}
