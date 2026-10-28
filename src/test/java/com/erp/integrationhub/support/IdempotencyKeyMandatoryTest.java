package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.result.PaymentResult;
import com.erp.integrationhub.application.usecase.ProcessPaymentUseCase;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import com.erp.integrationhub.interfaces.rest.controller.PaymentIntegrationController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying that Idempotency-Key header is mandatory for charge operations.
 */
class IdempotencyKeyMandatoryTest {

    private static final UUID TENANT_ID = UUID.fromString("12121212-3434-5656-7878-909090909090");

    @BeforeEach
    void setUp() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), TENANT_ID, "principal",
                Set.of("ROLE_ADMIN_TALLER"), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A charge without Idempotency-Key header is rejected with 400 and does not reach use case")
    void chargeWithoutIdempotencyKeyIsRejected() throws Exception {
        ProcessPaymentUseCase useCase = mock(ProcessPaymentUseCase.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentIntegrationController(useCase)).build();

        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBody()))
                .andExpect(status().isBadRequest());

        verify(useCase, never()).processPayment(any());
    }

    @Test
    @DisplayName("A charge with Idempotency-Key header succeeds and reaches use case")
    void chargeWithIdempotencyKeyReachesUseCase() throws Exception {
        ProcessPaymentUseCase useCase = mock(ProcessPaymentUseCase.class);
        configureSuccessResult(useCase);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentIntegrationController(useCase)).build();

        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("X-Tenant-ID", TENANT_ID.toString())
                        .header("Idempotency-Key", "key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBody()))
                .andExpect(status().isOk());

        verify(useCase).processPayment(any());
    }

    private void configureSuccessResult(ProcessPaymentUseCase useCase) {
        org.mockito.Mockito.when(useCase.processPayment(any()))
                .thenReturn(new PaymentResult(
                        UUID.randomUUID(), true, "SUCCEEDED", "ch_1", null, "AUTH-1", null, false));
    }

    private String buildBody() {
        return """
                {
                  "tenantId": "%s",
                  "paymentId": "%s",
                  "monto": 25.00,
                  "moneda": "PEN",
                  "tokenTarjeta": "tok_test",
                  "descripcion": "Mandatory key test",
                  "clienteEmail": "client@workshop.pe"
                }
                """.formatted(TENANT_ID, UUID.randomUUID());
    }
}
