package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IntegrationAdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @Mock
    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Mock
    private ProcessRetryQueueUseCase processRetryQueueUseCase;

    @BeforeEach
    void setUp() {
        IntegrationAdminController controller = new IntegrationAdminController(
                attemptRepositoryPort,
                retryQueueRepositoryPort,
                processRetryQueueUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/attempts - Should return list of attempts")
    void getAttempts_ShouldReturnList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        IntegrationAttempt attempt = IntegrationAttempt.ok(tenantId, ProviderType.NUBEFACT, "EMIT_VOUCHER", refId, 1);
        when(attemptRepositoryPort.findByTenantIdAndReferenceId(eq(tenantId), eq(refId)))
                .thenReturn(List.of(attempt));

        mockMvc.perform(get("/api/v1/integrations/admin/attempts")
                        .header("X-Tenant-ID", tenantId.toString())
                        .param("referenceId", refId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("NUBEFACT"))
                .andExpect(jsonPath("$[0].status").value("OK"));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/dlq - Should return DLQ tasks")
    void getDlqTasks_ShouldReturnList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        RetryTask dlqTask = RetryTask.create(tenantId, ProviderType.NUBEFACT, "EMIT_VOUCHER", UUID.randomUUID(), "{}", 5);

        when(retryQueueRepositoryPort.findDlqTasks(eq(tenantId), eq(50)))
                .thenReturn(List.of(dlqTask));

        mockMvc.perform(get("/api/v1/integrations/admin/dlq")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("NUBEFACT"));
    }

    @Test
    @DisplayName("POST /api/v1/integrations/admin/retry/trigger - Should trigger retry worker")
    void triggerRetryWorker_ShouldReturn200() throws Exception {
        when(processRetryQueueUseCase.processPendingRetries(50)).thenReturn(3);

        mockMvc.perform(post("/api/v1/integrations/admin/retry/trigger")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedTasks").value(3));
    }
}
