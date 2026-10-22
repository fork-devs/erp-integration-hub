package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
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

    @Mock
    private com.erp.integrationhub.domain.usecase.ReactivateRetryTaskUseCase reactivateUseCase;

    @BeforeEach
    void setUp() {
        IntegrationAdminController controller = new IntegrationAdminController(
                reactivateUseCase,
                attemptRepositoryPort,
                retryQueueRepositoryPort,
                processRetryQueueUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/attempts - Should return list of attempts")
    void getAttempts_ShouldReturnList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                tenantId,
                "admin@taller.pe",
                Set.of("ROLE_ADMIN"),
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        IntegrationAttempt attempt = IntegrationAttempt.ok(tenantId, ProviderType.APISUNAT, "EMIT_VOUCHER", refId, 1);
        when(attemptRepositoryPort.findByTenantIdAndReferenceId(eq(tenantId), eq(refId)))
                .thenReturn(List.of(attempt));

        mockMvc.perform(get("/api/v1/integrations/admin/attempts")
                        .header("X-Tenant-ID", tenantId.toString())
                        .param("referenceId", refId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("APISUNAT"))
                .andExpect(jsonPath("$[0].status").value("OK"));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/attempts - When header X-Tenant-ID is missing, use authenticated user tenantId")
    void getAttempts_WithoutHeader_ShouldUseAuthenticatedUserTenant() throws Exception {
        UUID authenticatedTenantId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                authenticatedTenantId,
                "admin@taller.pe",
                Set.of("ROLE_ADMIN"),
                false
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        IntegrationAttempt attempt = IntegrationAttempt.ok(authenticatedTenantId, ProviderType.APISUNAT, "EMIT_VOUCHER", UUID.randomUUID(), 1);
        when(attemptRepositoryPort.findByTenantId(eq(authenticatedTenantId), eq(50)))
                .thenReturn(List.of(attempt));

        mockMvc.perform(get("/api/v1/integrations/admin/attempts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value(authenticatedTenantId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/attempts - Reject cross-tenant access with 403 Forbidden")
    void getAttempts_WithForbiddenTenantHeader_ShouldReturn403() throws Exception {
        UUID myTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                myTenantId,
                "admin@taller.pe",
                Set.of("ROLE_ADMIN"),
                false
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        mockMvc.perform(get("/api/v1/integrations/admin/attempts")
                        .header("X-Tenant-ID", foreignTenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/integrations/admin/dlq - Should return DLQ tasks")
    void getDlqTasks_ShouldReturnList() throws Exception {
        UUID tenantId = UUID.randomUUID();
        RetryTask dlqTask = RetryTask.create(tenantId, ProviderType.APISUNAT, "EMIT_VOUCHER", UUID.randomUUID(), "{}", 5);

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                tenantId,
                "admin@taller.pe",
                Set.of("ROLE_ADMIN"),
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        when(retryQueueRepositoryPort.findDlqTasks(eq(tenantId), eq(50)))
                .thenReturn(List.of(dlqTask));

        mockMvc.perform(get("/api/v1/integrations/admin/dlq")
                        .header("X-Tenant-ID", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("APISUNAT"));
    }

    @Test
    @DisplayName("POST /api/v1/integrations/admin/retry/trigger - Should trigger retry worker when ADMIN")
    void triggerRetryWorker_AsAdmin_ShouldReturn200() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "admin@taller.pe",
                Set.of("ROLE_ADMIN"),
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        when(processRetryQueueUseCase.processPendingRetries(50)).thenReturn(3);

        mockMvc.perform(post("/api/v1/integrations/admin/retry/trigger")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedTasks").value(3));
    }

    @Test
    @DisplayName("POST /api/v1/integrations/admin/retry/trigger - Should reject when caller lacks ADMIN or INTERNAL_SERVICE role")
    void triggerRetryWorker_AsRegularUser_ShouldReturn403() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "mecanico@taller.pe",
                Set.of("ROLE_USER"),
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        mockMvc.perform(post("/api/v1/integrations/admin/retry/trigger")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
