package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.result.PagoResult;
import com.erp.integrationhub.application.usecase.ProcesarPagoUseCase;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentIntegrationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProcesarPagoUseCase procesarPagoUseCase;

    @BeforeEach
    void setUp() {
        PaymentIntegrationController controller = new PaymentIntegrationController(procesarPagoUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/integrations/payments/charge - Should return 200 OK with charge result")
    void processCharge_WhenValidRequest_ShouldReturn200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PagoResult result = new PagoResult(
                paymentId,
                true,
                "PAID",
                "ch_mock_123456",
                "pi_secret_mock",
                "AUTH-1234",
                null,
                false
        );
        when(procesarPagoUseCase.procesarPago(any())).thenReturn(result);

        String jsonPayload = """
                {
                    "paymentId": "%s",
                    "monto": 250.00,
                    "moneda": "PEN",
                    "tokenTarjeta": "tok_visa",
                    "descripcion": "Servicio de afinamiento de motor",
                    "clienteEmail": "cliente@taller.pe"
                }
                """.formatted(paymentId);

        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("X-Tenant-ID", tenantId.toString())
                        .header("Idempotency-Key", "idem-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.estado").value("PAID"))
                .andExpect(jsonPath("$.chargeId").value("ch_mock_123456"))
                .andExpect(jsonPath("$.codigoAutorizacion").value("AUTH-1234"));
    }
}
