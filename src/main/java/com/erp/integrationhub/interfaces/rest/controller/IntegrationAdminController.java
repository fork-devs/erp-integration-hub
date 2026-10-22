package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import com.erp.integrationhub.interfaces.rest.response.IntegrationAttemptResponse;
import com.erp.integrationhub.interfaces.rest.response.RetryTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/admin")
@Tag(name = "Integration Administration", description = "Endpoints for monitoring integration attempts, DLQ (Dead Letter Queue), and triggering retry processing")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
public class IntegrationAdminController {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final ProcessRetryQueueUseCase processRetryQueueUseCase;

    public IntegrationAdminController(
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            ProcessRetryQueueUseCase processRetryQueueUseCase) {
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.processRetryQueueUseCase = processRetryQueueUseCase;
    }

    @GetMapping("/attempts")
    @Operation(summary = "Query integration attempts", description = "Fetches integration attempt audit logs by reference ID or status with strict tenant isolation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    public ResponseEntity<List<IntegrationAttemptResponse>> getAttempts(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
            @RequestParam(value = "referenceId", required = false) UUID referenceId,
            @RequestParam(value = "status", required = false) AttemptStatus status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        AuthenticatedUser authUser = getAuthenticatedUser();

        // Si el header X-Tenant-ID falta, resolver el tenant del usuario autenticado
        if (tenantId == null && authUser != null && authUser.tenantId() != null) {
            tenantId = authUser.tenantId();
        }

        // Validación estricta de acceso al tenant: usuarios regulares/tenant admins no pueden consultar globalmente
        // Sin taller, solo quien tiene alcance transversal puede consultar en
        // global. La credencial compartida de servicio interno ya no concede
        // este alcance: es una credencial de servicio, no de administracion de
        // plataforma.
        if (tenantId == null && (authUser == null || !authUser.isSuperAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (tenantId != null && authUser != null && !authUser.canOperateOnTenant(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final UUID tallerEfectivo = tenantId;

        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<IntegrationAttempt> attempts;
        if (tenantId != null) {
            if (referenceId != null) {
                attempts = enContextoDeTaller(tallerEfectivo, () -> attemptRepositoryPort.findByTenantIdAndReferenceId(tallerEfectivo, referenceId));
            } else {
                attempts = enContextoDeTaller(tallerEfectivo, () -> attemptRepositoryPort.findByTenantId(tallerEfectivo, effectiveLimit));
            }
        } else if (status != null) {
            attempts = enContextoDeTaller(tallerEfectivo, () -> attemptRepositoryPort.findByTenantIdAndStatus(tallerEfectivo, status, effectiveLimit));
        } else {
            attempts = enContextoDeTaller(tallerEfectivo, () -> attemptRepositoryPort.findByTenantIdAndStatus(tallerEfectivo, AttemptStatus.OK, effectiveLimit));
        }

        List<IntegrationAttemptResponse> response = attempts.stream()
                .map(IntegrationAttemptResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dlq")
    @Operation(summary = "List Dead Letter Queue tasks", description = "Fetches failed integration tasks that exceeded maximum retry attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    public ResponseEntity<List<RetryTaskResponse>> getDlqTasks(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        AuthenticatedUser authUser = getAuthenticatedUser();

        if (tenantId == null && authUser != null && authUser.tenantId() != null) {
            tenantId = authUser.tenantId();
        }

        // Sin taller, solo quien tiene alcance transversal puede consultar en
        // global. La credencial compartida de servicio interno ya no concede
        // este alcance: es una credencial de servicio, no de administracion de
        // plataforma.
        if (tenantId == null && (authUser == null || !authUser.isSuperAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (tenantId != null && authUser != null && !authUser.canOperateOnTenant(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final UUID tallerEfectivo = tenantId;

        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<RetryTask> tasks = enContextoDeTaller(tallerEfectivo, () -> retryQueueRepositoryPort.findDlqTasks(tallerEfectivo, effectiveLimit));
        List<RetryTaskResponse> response = tasks.stream()
                .map(RetryTaskResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/retry/trigger")
    @Operation(summary = "Trigger retry worker manually", description = "Executes the retry queue processor on-demand (ADMIN / INTERNAL_SERVICE only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    public ResponseEntity<Map<String, Object>> triggerRetryWorker() {
        AuthenticatedUser authUser = getAuthenticatedUser();
        if (authUser == null || (!authUser.isAdmin() && !authUser.isInternalService())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        int processed = processRetryQueueUseCase.processPendingRetries(DEFAULT_LIMIT);
        return ResponseEntity.ok(Map.of(
                "message", "Retry processing cycle executed successfully",
                "processedTasks", processed
        ));
    }

    /**
     * Ejecuta la operacion con el contexto de taller establecido, de modo que
     * la politica de Row-Level Security filtre por el taller del principal.
     */
    private <T> T enContextoDeTaller(UUID tenantId, java.util.function.Supplier<T> operacion) {
        if (tenantId == null) {
            return operacion.get();
        }
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operacion.get();
        }
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
