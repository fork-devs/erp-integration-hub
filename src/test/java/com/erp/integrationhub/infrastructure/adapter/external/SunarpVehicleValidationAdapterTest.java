package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryPlateResult;
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

class SunarpVehicleValidationAdapterTest {

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
    @DisplayName("Should parse standard flat JSON with spaces correctly")
    void queryPlate_WhenFlatJsonWithSpaces_ReturnsValidResult() {
        String json = """
                {
                  "placa": "ABC123",
                  "marca": "TOYOTA",
                  "modelo": "COROLLA",
                  "vin": "9BRBA38E454000123",
                  "numero_motor": "1NZ-1234567",
                  "anio_fabricacion": "2022",
                  "color": "ROJO",
                  "combustible": "GASOLINA"
                }
                """;
        configureServerResponse("/placa", 200, json);

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        QueryPlateResult result = adapter.queryPlate("ABC-123");

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("ABC123", result.plate());
        assertEquals("TOYOTA", result.brand());
        assertEquals("COROLLA", result.model());
        assertEquals("9BRBA38E454000123", result.vin());
        assertEquals("1NZ-1234567", result.engineNumber());
        assertEquals("2022", result.manufactureYear());
        assertEquals("ROJO", result.color());
        assertEquals("GASOLINA", result.fuelType());
    }

    @Test
    @DisplayName("Should parse nested data object correctly")
    void queryPlate_WhenNestedDataObject_ReturnsValidResult() {
        String json = """
                {
                  "success": true,
                  "data": {
                    "placa": "XYZ789",
                    "marca": "NISSAN",
                    "modelo": "SENTRA",
                    "serie": "3N1AB7AP6KL123456",
                    "motor": "HR16-9876543",
                    "anio": "2021",
                    "color": "PLATA",
                    "combustible": "GNV"
                  }
                }
                """;
        configureServerResponse("/placa", 200, json);

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        QueryPlateResult result = adapter.queryPlate("XYZ789");

        assertNotNull(result);
        assertTrue(result.valid());
        assertEquals("NISSAN", result.brand());
        assertEquals("SENTRA", result.model());
        assertEquals("3N1AB7AP6KL123456", result.vin());
        assertEquals("HR16-9876543", result.engineNumber());
        assertEquals("2021", result.manufactureYear());
        assertEquals("PLATA", result.color());
        assertEquals("GNV", result.fuelType());
    }

    @Test
    @DisplayName("Should return invalid result when plate is not found (HTTP 404)")
    void queryPlate_WhenNotFound404_ReturnsInvalidResult() {
        configureServerResponse("/placa", 404, "{\"error\": \"Plate not found\"}");

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        QueryPlateResult result = adapter.queryPlate("ABC123");

        assertNotNull(result);
        assertFalse(result.valid());
        assertNull(result.brand());
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException on HTTP 429 rate limit")
    void queryPlate_WhenRateLimited429_ThrowsProviderUnavailableException() {
        configureServerResponse("/placa", 429, "{\"error\": \"Rate limit exceeded\"}");

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryPlate("ABC123"));
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException on HTTP 500 server error")
    void queryPlate_WhenServerError500_ThrowsProviderUnavailableException() {
        configureServerResponse("/placa", 500, "{\"error\": \"Internal server error\"}");

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryPlate("ABC123"));
    }

    @Test
    @DisplayName("Should return invalid without network request when plate format is too short")
    void queryPlate_WhenShortPlate_ReturnsInvalidWithoutRequest() {
        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);

        QueryPlateResult result1 = adapter.queryPlate("AB");
        assertFalse(result1.valid());

        QueryPlateResult result2 = adapter.queryPlate(null);
        assertFalse(result2.valid());
    }

    @Test
    @DisplayName("Should throw ProviderUnavailableException when upstream server is down")
    void queryPlate_WhenServerDown_ThrowsProviderUnavailableException() {
        server.stop(0);
        server = null;

        SunarpVehicleValidationAdapter adapter = new SunarpVehicleValidationAdapter(serverUrl, "test-token", false);
        assertThrows(ProviderUnavailableException.class, () -> adapter.queryPlate("ABC123"));
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
