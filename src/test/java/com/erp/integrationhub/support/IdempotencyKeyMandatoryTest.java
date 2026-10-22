package com.erp.integrationhub.support;

import com.erp.integrationhub.interfaces.rest.controller.PaymentIntegrationController;
import com.erp.integrationhub.interfaces.rest.support.AuthenticatedUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La clave de idempotencia es obligatoria en el cobro.
 *
 * <p>Antes se derivaba de los datos del cobro. Con la derivacion, la clave
 * era la misma para dos cobros identicos del mismo cliente: el segundo se
 * tomaba por una repeticion del primero y NO se cobraba. El cliente pagaba
 * una vez por dos servicios.
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgo P0 5.11.
 */
class IdempotencyKeyMandatoryTest {

    private static final UUID TALLER = UUID.fromString("12121212-3434-5656-7878-909090909090");

    @BeforeEach
    void setUp() {
        // El principal autenticado es el del taller: sin el, la peticion no
        // llega ni a la comprobacion de la cabecera.
        var usuario = new com.erp.integrationhub.infrastructure.security.AuthenticatedUser(
                UUID.randomUUID(), TALLER, "principal",
                java.util.Set.of("ROLE_ADMIN_TALLER"), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Un cobro sin la cabecera de clave se rechaza y no llega al caso de uso")
    void unCobroSinClaveSeRechazaYNoLlegaAlCasoDeUso() throws Exception {
        var casoDeUso = mock(com.erp.integrationhub.application.usecase.ProcesarPagoUseCase.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentIntegrationController(casoDeUso)).build();

        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("X-Tenant-ID", TALLER.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isBadRequest());

        verify(casoDeUso, never()).procesarPago(any());
    }

    @Test
    @DisplayName("Un cobro con la cabecera de clave llega al caso de uso")
    void unCobroConClaveLlegaAlCasoDeUso() throws Exception {
        var casoDeUso = mock(com.erp.integrationhub.application.usecase.ProcesarPagoUseCase.class);
        whenResultadoCorrecto(casoDeUso);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentIntegrationController(casoDeUso)).build();

        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("X-Tenant-ID", TALLER.toString())
                        .header("Idempotency-Key", "clave-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo()))
                .andExpect(status().isOk());

        verify(casoDeUso).procesarPago(any());
    }

    private void whenResultadoCorrecto(
            com.erp.integrationhub.application.usecase.ProcesarPagoUseCase casoDeUso) {
        org.mockito.Mockito.when(casoDeUso.procesarPago(any()))
                .thenReturn(new com.erp.integrationhub.application.dto.result.PagoResult(
                        UUID.randomUUID(), true, "SUCCEEDED", "ch_1", null, "AUTH-1", null, false));
    }

    private String cuerpo() {
        return """
                {
                  "tenantId": "%s",
                  "paymentId": "%s",
                  "monto": 25.00,
                  "moneda": "PEN",
                  "tokenTarjeta": "tok_test",
                  "descripcion": "Prueba de clave obligatoria",
                  "clienteEmail": "cliente@taller.pe"
                }
                """.formatted(TALLER, UUID.randomUUID());
    }
}
