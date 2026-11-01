package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.QueryPersonalIdentityResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FactilizaIdentityValidationAdapterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ObjectMapper objectMapper;
    private FactilizaIdentityValidationAdapter adapter;

    private static final String BASE_URL = "https://api.factiliza.com/pe/v1";
    private static final String TOKEN = "test_bearer_token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new FactilizaIdentityValidationAdapter(BASE_URL, TOKEN, httpClient, objectMapper);
    }

    @Test
    @DisplayName("queryDni - Should parse valid RENIEC response with personal details")
    void queryDni_Success() throws Exception {
        String json = """
                {
                  "status": 200,
                  "message": "Exito",
                  "data": {
                    "numero": "72345678",
                    "nombres": "JUAN CARLOS",
                    "apellido_paterno": "PEREZ",
                    "apellido_materno": "GOMEZ",
                    "nombre_completo": "PEREZ GOMEZ, JUAN CARLOS",
                    "codigo_verificacion": "3"
                  }
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        QueryPersonalIdentityResult result = adapter.queryDni("72345678");

        assertThat(result.valid()).isTrue();
        assertThat(result.documentType()).isEqualTo("DNI");
        assertThat(result.documentNumber()).isEqualTo("72345678");
        assertThat(result.firstName()).isEqualTo("JUAN CARLOS");
        assertThat(result.firstSurname()).isEqualTo("PEREZ");
        assertThat(result.secondSurname()).isEqualTo("GOMEZ");
        assertThat(result.fullName()).isEqualTo("PEREZ GOMEZ, JUAN CARLOS");
        assertThat(result.verificationCode()).isEqualTo("3");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest req = captor.getValue();
        assertThat(req.uri().toString()).isEqualTo("https://api.factiliza.com/pe/v1/dni/info/72345678");
        assertThat(req.headers().firstValue("Authorization")).contains("Bearer " + TOKEN);
    }

    @Test
    @DisplayName("queryDni - Invalid DNI format should return invalid without calling upstream")
    void queryDni_InvalidFormat() {
        QueryPersonalIdentityResult result = adapter.queryDni("1234");
        assertThat(result.valid()).isFalse();
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("queryDni - HTTP 404 Not Found returns invalid result")
    void queryDni_NotFound() throws Exception {
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        QueryPersonalIdentityResult result = adapter.queryDni("99999999");

        assertThat(result.valid()).isFalse();
        assertThat(result.documentNumber()).isEqualTo("99999999");
    }

    @Test
    @DisplayName("queryCee - Should parse valid Migraciones foreign resident response")
    void queryCee_Success() throws Exception {
        String json = """
                {
                  "status": 200,
                  "message": "Exito",
                  "data": {
                    "numero": "001234567",
                    "nombres": "JOHN EDWARD",
                    "apellidoPaterno": "DOE",
                    "apellidoMaterno": "SMITH",
                    "nombreCompleto": "DOE SMITH, JOHN EDWARD"
                  }
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        QueryPersonalIdentityResult result = adapter.queryCee("001234567");

        assertThat(result.valid()).isTrue();
        assertThat(result.documentType()).isEqualTo("CE");
        assertThat(result.documentNumber()).isEqualTo("001234567");
        assertThat(result.firstName()).isEqualTo("JOHN EDWARD");
        assertThat(result.firstSurname()).isEqualTo("DOE");
        assertThat(result.fullName()).isEqualTo("DOE SMITH, JOHN EDWARD");
    }

    @Test
    @DisplayName("queryDni - Rate limit HTTP 429 throws ProviderUnavailableException")
    void queryDni_RateLimitThrows() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThatThrownBy(() -> adapter.queryDni("72345678"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    @DisplayName("queryDni - Network failure throws ProviderUnavailableException")
    void queryDni_NetworkFailureThrows() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection timed out"));

        assertThatThrownBy(() -> adapter.queryDni("72345678"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("Communication failure");
    }
}
