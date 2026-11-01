package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.QueryPlateCommand;
import com.erp.integrationhub.application.dto.command.QueryRucCommand;
import com.erp.integrationhub.application.dto.result.PersonalIdentityResult;
import com.erp.integrationhub.application.dto.result.PlateResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.QueryPersonalIdentityUseCase;
import com.erp.integrationhub.application.usecase.QueryPlateUseCase;
import com.erp.integrationhub.application.usecase.QueryRucUseCase;
import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * REST controller for external identity (SUNAT RUC, Factiliza DNI/CE) and vehicle registration (SUNARP plate) validations.
 */
@RestController
@RequestMapping({"/api/v1/integrations/validate", "/api/v1/identity-validations"})
@Tag(name = "Identity & Vehicle Validation", description = "Validates Peruvian RUC against SUNAT/RENIEC and vehicle license plates against SUNARP with high-performance caching")
public class IdentityValidationController {

    private final QueryRucUseCase queryRucUseCase;
    private final QueryPlateUseCase queryPlateUseCase;
    private final QueryPersonalIdentityUseCase queryPersonalIdentityUseCase;
    private final TenantRateLimiter rateLimiter;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityValidationController(
            QueryRucUseCase queryRucUseCase,
            QueryPlateUseCase queryPlateUseCase,
            QueryPersonalIdentityUseCase queryPersonalIdentityUseCase,
            TenantRateLimiter rateLimiter) {
        this.queryRucUseCase = queryRucUseCase;
        this.queryPlateUseCase = queryPlateUseCase;
        this.queryPersonalIdentityUseCase = queryPersonalIdentityUseCase;
        this.rateLimiter = rateLimiter;
    }

    public IdentityValidationController(
            QueryRucUseCase queryRucUseCase,
            QueryPlateUseCase queryPlateUseCase,
            TenantRateLimiter rateLimiter) {
        this(queryRucUseCase, queryPlateUseCase, null, rateLimiter);
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
     * Executes identity lookup query (DNI, CE, RUC) across official registries.
     */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ADMIN_TALLER', 'SUPERADMIN', 'ASESOR', 'MECANICO', 'INTERNAL_SERVICE', 'ROLE_FLEET')")
    @Operation(summary = "Lookup Peruvian identity document (RUC, DNI, CE)")
    public ResponseEntity<Map<String, Object>> lookup(
            @RequestParam(name = "type", required = false, defaultValue = "RUC") String type,
            @RequestParam(name = "number", required = false) String number,
            @RequestParam(name = "documentType", required = false) String docType,
            @RequestParam(name = "documentNumber", required = false) String docNumber,
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID requestedTenantId
    ) {
        String resolvedType = (docType != null ? docType : type).toUpperCase().trim();
        String resolvedNumber = (docNumber != null ? docNumber : (number != null ? number : "")).trim();

        UUID effectiveTenantId = requestedTenantId;
        if (effectiveTenantId == null) {
            AuthenticatedUser currentUser = AuthenticatedUserHolder.current();
            if (currentUser != null && currentUser.tenantId() != null && !currentUser.tenantId().equals(TenantResolver.PLATFORM_TENANT_ID)) {
                effectiveTenantId = currentUser.tenantId();
            } else {
                effectiveTenantId = TenantResolver.PLATFORM_TENANT_ID;
            }
        }

        if ("RUC".equalsIgnoreCase(resolvedType) && resolvedNumber.length() == 11) {
            try {
                UUID tenant = effectiveTenantId;
                RucResult rucResult = withTenantContext(tenant, () ->
                        queryRucUseCase.queryRuc(new QueryRucCommand(tenant, resolvedNumber)));
                if (rucResult != null && rucResult.valid()) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", "RUC");
                    response.put("documentNumber", rucResult.ruc());
                    response.put("businessName", rucResult.legalName() != null ? rucResult.legalName() : "");
                    response.put("tradeName", rucResult.legalName() != null ? rucResult.legalName() : "");
                    response.put("address", rucResult.fiscalAddress() != null ? rucResult.fiscalAddress() : "");
                    response.put("condition", rucResult.condition() != null ? rucResult.condition() : "HABIDO");
                    response.put("status", rucResult.status() != null ? rucResult.status() : "ACTIVO");
                    response.put("verified", true);
                    response.put("source", "SUNAT");
                    return ResponseEntity.ok(response);
                }
            } catch (Exception ignored) {}
        }

        if (queryPersonalIdentityUseCase != null &&
                ("DNI".equalsIgnoreCase(resolvedType) || "CE".equalsIgnoreCase(resolvedType) || "CEE".equalsIgnoreCase(resolvedType) || "CARNET_EXTRANJERIA".equalsIgnoreCase(resolvedType))) {
            try {
                UUID tenant = effectiveTenantId;
                PersonalIdentityResult identity = withTenantContext(tenant, () ->
                        queryPersonalIdentityUseCase.queryPersonalIdentity(tenant, resolvedType, resolvedNumber));
                if (identity != null && identity.valid()) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", resolvedType);
                    response.put("documentNumber", identity.documentNumber());
                    response.put("firstName", identity.firstName() != null ? identity.firstName() : "");
                    response.put("lastName", identity.firstSurname() != null ? identity.firstSurname() : "");
                    response.put("firstSurname", identity.firstSurname() != null ? identity.firstSurname() : "");
                    response.put("secondSurname", identity.secondSurname() != null ? identity.secondSurname() : "");
                    response.put("fullName", identity.fullName() != null ? identity.fullName() : "");
                    response.put("verified", true);
                    response.put("source", ("CE".equalsIgnoreCase(resolvedType) || "CEE".equalsIgnoreCase(resolvedType)) ? "MIGRACIONES" : "RENIEC");
                    return ResponseEntity.ok(response);
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> notFound = new LinkedHashMap<>();
        notFound.put("type", resolvedType);
        notFound.put("documentNumber", resolvedNumber);
        notFound.put("verified", false);
        notFound.put("source", "DNI".equalsIgnoreCase(resolvedType) ? "RENIEC" : "SUNAT");
        notFound.put("message", "Documento no encontrado o no valido ante la entidad oficial");
        return ResponseEntity.ok(notFound);
    }

    private <T> T withTenantContext(UUID tenantId, Supplier<T> operation) {
        try (TenantContextScope scope = TenantContextScope.forTenant(tenantId)) {
            return operation.get();
        }
    }
}
