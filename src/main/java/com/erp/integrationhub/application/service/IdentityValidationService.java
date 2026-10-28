package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.QueryPlateCommand;
import com.erp.integrationhub.application.dto.command.QueryRucCommand;
import com.erp.integrationhub.application.dto.result.PlateResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.QueryPlateUseCase;
import com.erp.integrationhub.application.usecase.QueryRucUseCase;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for taxpayer (RUC) and vehicle plate identity validation.
 */
public class IdentityValidationService implements QueryRucUseCase, QueryPlateUseCase {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationService.class);

    private final IdentityValidatorPort identityValidatorPort;
    private final VehicleValidatorPort vehicleValidatorPort;
    private final IdentityCachePort identityCachePort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final UnitOfWork unitOfWork;

    public IdentityValidationService(
            IdentityValidatorPort identityValidatorPort,
            VehicleValidatorPort vehicleValidatorPort,
            IdentityCachePort identityCachePort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this.identityValidatorPort = identityValidatorPort;
        this.vehicleValidatorPort = vehicleValidatorPort;
        this.identityCachePort = identityCachePort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public RucResult queryRuc(QueryRucCommand command) {
        if (command.ruc() == null || command.ruc().trim().length() != 11) {
            throw new IllegalArgumentException("RUC must contain exactly 11 digits");
        }

        String ruc = command.ruc().trim();
        Optional<QueryRucResult> cached = identityCachePort != null ? identityCachePort.getRuc(ruc) : Optional.empty();
        if (cached.isPresent()) {
            log.debug("RUC {} retrieved from cache (Redis / L1)", ruc);
            return toRucResult(cached.get(), true);
        }

        QueryRucResult result;
        try {
            result = identityValidatorPort.queryRuc(ruc);
            if (result.valid() && identityCachePort != null) {
                identityCachePort.putRuc(ruc, result);
            }
            logAttempt(command.tenantId(), ProviderType.CHEQUEA, "VALIDATE_RUC", ruc, null);
        } catch (Exception e) {
            log.warn("Failed to validate RUC {} with external provider: {}", ruc, e.getMessage());
            result = QueryRucResult.invalid(ruc);
            logAttempt(command.tenantId(), ProviderType.CHEQUEA, "VALIDATE_RUC", ruc, e.getMessage());
        }

        return toRucResult(result, false);
    }

    @Override
    public PlateResult queryPlate(QueryPlateCommand command) {
        if (command.plate() == null || command.plate().trim().isEmpty()) {
            throw new IllegalArgumentException("Vehicle license plate is required");
        }

        String plate = command.plate().trim().toUpperCase().replace("-", "");
        Optional<QueryPlateResult> cached = identityCachePort != null ? identityCachePort.getPlaca(plate) : Optional.empty();
        if (cached.isPresent()) {
            log.debug("Vehicle plate {} retrieved from cache (Redis / L1)", plate);
            return toPlateResult(cached.get(), true);
        }

        QueryPlateResult result;
        try {
            result = vehicleValidatorPort.queryPlate(plate);
            if (result.valid() && identityCachePort != null) {
                identityCachePort.putPlaca(plate, result);
            }
            logAttempt(command.tenantId(), ProviderType.SUNARP, "VALIDATE_PLATE", plate, null);
        } catch (Exception e) {
            log.warn("Failed to query vehicle plate {} with external provider: {}", plate, e.getMessage());
            result = QueryPlateResult.invalid(plate);
            logAttempt(command.tenantId(), ProviderType.SUNARP, "VALIDATE_PLATE", plate, e.getMessage());
        }

        return toPlateResult(result, false);
    }

    private void logAttempt(UUID tenantId, ProviderType provider, String op, String referenceKey, String error) {
        UUID effectiveTenant = tenantId;
        UUID referenceId = (referenceKey != null && !referenceKey.isBlank())
                ? UUID.nameUUIDFromBytes(referenceKey.getBytes(StandardCharsets.UTF_8))
                : UUID.randomUUID();

        unitOfWork.runInTransaction(() -> {
            int attemptNumber = 1 + attemptRepositoryPort.countAttempts(
                    effectiveTenant, provider, op, referenceId);
            attemptRepositoryPort.save(error == null
                    ? IntegrationAttempt.ok(effectiveTenant, provider, op, referenceId, attemptNumber)
                    : IntegrationAttempt.retry(effectiveTenant, provider, op, referenceId, attemptNumber, error)
            );
        });
    }

    private RucResult toRucResult(QueryRucResult r, boolean fromCache) {
        return new RucResult(
                r.ruc(),
                r.legalName(),
                r.status(),
                r.condition(),
                r.fiscalAddress(),
                r.department(),
                r.province(),
                r.district(),
                r.ubigeo(),
                r.valid(),
                fromCache
        );
    }

    private PlateResult toPlateResult(QueryPlateResult p, boolean fromCache) {
        return new PlateResult(
                p.plate(),
                p.brand(),
                p.model(),
                p.vin(),
                p.engineNumber(),
                p.manufactureYear(),
                p.color(),
                p.fuelType(),
                p.valid(),
                fromCache
        );
    }
}
