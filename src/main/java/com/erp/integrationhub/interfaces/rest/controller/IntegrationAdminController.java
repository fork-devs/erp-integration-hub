package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.usecase.ReactivateRetryTaskUseCase;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import com.erp.integrationhub.interfaces.rest.response.IntegrationAttemptResponse;
import com.erp.integrationhub.interfaces.rest.response.RetryTaskResponse;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST controller for monitoring integration attempts, DLQ management, and manual retry triggering.
 */
@RestController
@RequestMapping("/api/v1/integrations/admin")
@Tag(name = "Integration Administration", description = "Endpoints for monitoring integration attempts, DLQ (Dead Letter Queue), and triggering retry processing")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
public class IntegrationAdminController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationAdminController.class);

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final RetryQueueRepositoryPort retryQueueRepositoryPort;
    private final ProcessRetryQueueUseCase processRetryQueueUseCase;
    private final ReactivateRetryTaskUseCase reactivateRetryTaskUseCase;

    public IntegrationAdminController(
            ReactivateRetryTaskUseCase reactivateRetryTaskUseCase,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            ProcessRetryQueueUseCase processRetryQueueUseCase) {
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.retryQueueRepositoryPort = retryQueueRepositoryPort;
        this.processRetryQueueUseCase = processRetryQueueUseCase;
        this.reactivateRetryTaskUseCase = reactivateRetryTaskUseCase;
    }

    /**
     * Queries integration attempts audit log with strict tenant isolation.
     */
    @GetMapping("/attempts")
    @Operation(summary = "Query integration attempts", description = "Fetches integration attempt audit logs by reference ID or status with strict tenant isolation")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    public ResponseEntity<List<IntegrationAttemptResponse>> getAttempts(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
            @RequestParam(value = "referenceId", required = false) UUID referenceId,
            @RequestParam(value = "status", required = false) AttemptStatus status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        AuthenticatedUser authUser = getAuthenticatedUser();

        // If X-Tenant-ID header is missing, resolve tenant from authenticated user principal
        if (tenantId == null && authUser != null && authUser.tenantId() != null) {
            tenantId = authUser.tenantId();
        }

        // Strict tenant access validation: non-super-admins cannot perform global cross-tenant queries
        if (tenantId == null && (authUser == null || !authUser.isSuperAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (tenantId != null && authUser != null && !authUser.canOperateOnTenant(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final UUID effectiveTenant = tenantId;
        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<IntegrationAttempt> attempts;

        if (tenantId != null) {
            if (referenceId != null) {
                attempts = withTenantContext(effectiveTenant, () -> attemptRepositoryPort.findByTenantIdAndReferenceId(effectiveTenant, referenceId));
            } else {
                attempts = withTenantContext(effectiveTenant, () -> attemptRepositoryPort.findByTenantId(effectiveTenant, effectiveLimit));
            }
        } else if (status != null) {
            attempts = withTenantContext(effectiveTenant, () -> attemptRepositoryPort.findByTenantIdAndStatus(effectiveTenant, status, effectiveLimit));
        } else {
            attempts = withTenantContext(effectiveTenant, () -> attemptRepositoryPort.findByTenantIdAndStatus(effectiveTenant, AttemptStatus.OK, effectiveLimit));
        }

        List<IntegrationAttemptResponse> response = attempts.stream()
                .map(IntegrationAttemptResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Lists tasks that have exceeded their maximum retry attempts and moved to DLQ.
     */
    @GetMapping("/dlq")
    @Operation(summary = "List Dead Letter Queue tasks", description = "Fetches failed integration tasks that exceeded maximum retry attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    public ResponseEntity<List<RetryTaskResponse>> getDlqTasks(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        AuthenticatedUser authUser = getAuthenticatedUser();
        logOperation("query_dead_letter_queue", authUser, tenantId);

        if (tenantId == null && authUser != null && authUser.tenantId() != null) {
            tenantId = authUser.tenantId();
        }

        if (tenantId == null && (authUser == null || !authUser.isSuperAdmin())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (tenantId != null && authUser != null && !authUser.canOperateOnTenant(tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final UUID effectiveTenant = tenantId;

        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<RetryTask> tasks = withTenantContext(effectiveTenant, () -> retryQueueRepositoryPort.findDlqTasks(effectiveTenant, effectiveLimit));
        List<RetryTaskResponse> response = tasks.stream()
                .map(RetryTaskResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Reactivates a dead-letter queue task, resetting its attempt count to 0.
     */
    @PostMapping("/dlq/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INTERNAL_SERVICE')")
    @Operation(summary = "Reactivate Dead Letter Queue task",
            description = "Returns the task to the pending retry queue with reset attempts counter")
    public ResponseEntity<Map<String, Object>> reactivateTask(@PathVariable UUID id) {
        RetryTask task = reactivateRetryTaskUseCase.reactivate(id);
        return ResponseEntity.ok(Map.of(
                "id", task.getId().toString(),
                "status", task.getStatus(),
                "attempts", task.getAttemptsCount(),
                "nextRetryAt", String.valueOf(task.getNextRetryAt())));
    }

    /**
     * Manually triggers a retry worker processing cycle.
     */
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
     * Executes the given operation within the workshop tenant context for proper RLS filtering.
     */
    private <T> T withTenantContext(UUID tenantId, Supplier<T> operation) {
        if (tenantId == null) {
            return operation.get();
        }
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operation.get();
        }
    }

    /**
     * Logs administrative operations for auditability.
     */
    private void logOperation(String operation, AuthenticatedUser authUser, UUID tenantId) {
        String identity = authUser != null && authUser.username() != null
                ? authUser.username()
                : "unknown";
        log.info("Administrative operation {} executed by {} on tenant {}",
                operation, identity, tenantId);
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
