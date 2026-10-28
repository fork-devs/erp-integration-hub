package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.QueryPlateCommand;
import com.erp.integrationhub.application.dto.command.QueryRucCommand;
import com.erp.integrationhub.application.dto.result.PlateResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.QueryPlateUseCase;
import com.erp.integrationhub.application.usecase.QueryRucUseCase;
import com.erp.integrationhub.infrastructure.security.TenantRateLimiter;
import com.erp.integrationhub.interfaces.rest.response.PlateResponse;
import com.erp.integrationhub.interfaces.rest.response.RucResponse;
import com.erp.integrationhub.interfaces.rest.support.AuthenticatedUserHolder;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import com.erp.integrationhub.interfaces.rest.support.TenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST controller for external identity (SUNAT RUC) and vehicle registration (SUNARP plate) validations.
 */
@RestController
@RequestMapping("/api/v1/integrations/validate")
@Tag(name = "Identity & Vehicle Validation", description = "Validates Peruvian RUC against SUNAT/RENIEC and vehicle license plates against SUNARP with high-performance caching")
public class IdentityValidationController {

    private final QueryRucUseCase queryRucUseCase;
    private final QueryPlateUseCase queryPlateUseCase;
    private final TenantRateLimiter rateLimiter;

    public IdentityValidationController(
            QueryRucUseCase queryRucUseCase,
            QueryPlateUseCase queryPlateUseCase,
            TenantRateLimiter rateLimiter) {
        this.queryRucUseCase = queryRucUseCase;
        this.queryPlateUseCase = queryPlateUseCase;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Validates an 11-digit Peruvian RUC via SUNAT registry.
     *
     * @param ruc 11-digit taxpayer registration number
     * @param requestedTenantId optional workshop tenant ID header
     * @return taxpayer details and fiscal status
     */
    @GetMapping("/ruc/{ruc}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'ASESOR', 'MECANICO', 'INTERNAL_SERVICE', 'ROLE_FLEET')")
    @Operation(summary = "Validate RUC via SUNAT", description = "Retrieves fiscal address, status (HABIDO), condition (ACTIVO) with 30-day TTL local cache")
    public ResponseEntity<RucResponse> validateRuc(
            @PathVariable("ruc") String ruc,
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId) {

        UUID effectiveTenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        if (!rateLimiter.tryAcquire(effectiveTenantId)) {
            long waitSeconds = rateLimiter.waitSeconds(effectiveTenantId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(waitSeconds))
                    .build();
        }

        QueryRucCommand command = new QueryRucCommand(effectiveTenantId, ruc);
        RucResult result = withTenantContext(effectiveTenantId, () -> queryRucUseCase.queryRuc(command));
        return ResponseEntity.ok(RucResponse.fromResult(result));
    }

    /**
     * Validates a vehicle license plate via SUNARP registry.
     *
     * @param plate vehicle license plate identifier
     * @param requestedTenantId optional workshop tenant ID header
     * @return vehicle specifications and registration details
     */
    @GetMapping("/placa/{placa}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'ASESOR', 'MECANICO', 'INTERNAL_SERVICE', 'ROLE_FLEET')")
    @Operation(summary = "Validate vehicle plate via SUNARP", description = "Retrieves VIN, brand, model, color, fuel type with 7-day TTL local cache")
    public ResponseEntity<PlateResponse> validatePlate(
            @PathVariable("placa") String plate,
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId) {

        UUID effectiveTenantId = TenantResolver.resolveForOperation(requestedTenantId, AuthenticatedUserHolder.current());

        if (!rateLimiter.tryAcquire(effectiveTenantId)) {
            long waitSeconds = rateLimiter.waitSeconds(effectiveTenantId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(waitSeconds))
                    .build();
        }

        QueryPlateCommand command = new QueryPlateCommand(effectiveTenantId, plate);
        PlateResult result = withTenantContext(effectiveTenantId, () -> queryPlateUseCase.queryPlate(command));
        return ResponseEntity.ok(PlateResponse.fromResult(result));
    }

    /**
     * Executes the given operation within the workshop tenant context for proper RLS filtering.
     */
    private <T> T withTenantContext(UUID tenantId, Supplier<T> operation) {
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operation.get();
        }
    }
}
