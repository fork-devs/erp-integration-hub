package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessRetryQueueUseCase;
import com.erp.integrationhub.domain.model.AttemptStatus;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.RetryTask;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.interfaces.rest.response.IntegrationAttemptResponse;
import com.erp.integrationhub.interfaces.rest.response.RetryTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
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
public class IntegrationAdminController {

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
    @Operation(summary = "Query integration attempts", description = "Fetches integration attempt audit logs by reference ID or status")
    public ResponseEntity<List<IntegrationAttemptResponse>> getAttempts(
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId,
            @RequestParam(value = "referenceId", required = false) UUID referenceId,
            @RequestParam(value = "status", required = false) AttemptStatus status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        List<IntegrationAttempt> attempts;
        if (tenantId != null && referenceId != null) {
            attempts = attemptRepositoryPort.findByTenantIdAndReferenceId(tenantId, referenceId);
        } else if (status != null) {
            attempts = attemptRepositoryPort.findByStatus(status, limit);
        } else {
            attempts = attemptRepositoryPort.findByStatus(AttemptStatus.OK, limit);
        }

        List<IntegrationAttemptResponse> response = attempts.stream()
                .map(IntegrationAttemptResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dlq")
    @Operation(summary = "List Dead Letter Queue tasks", description = "Fetches failed integration tasks that exceeded maximum retry attempts")
    public ResponseEntity<List<RetryTaskResponse>> getDlqTasks(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        List<RetryTask> tasks = retryQueueRepositoryPort.findDlqTasks(tenantId, limit);
        List<RetryTaskResponse> response = tasks.stream()
                .map(RetryTaskResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/retry/trigger")
    @Operation(summary = "Trigger retry worker manually", description = "Executes the retry queue processor on-demand to process pending failed tasks")
    public ResponseEntity<Map<String, Object>> triggerRetryWorker() {
        int processed = processRetryQueueUseCase.processPendingRetries(50);
        return ResponseEntity.ok(Map.of(
                "message", "Retry processing cycle executed successfully",
                "processedTasks", processed
        ));
    }
}
