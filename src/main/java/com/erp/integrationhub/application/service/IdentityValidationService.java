package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ConsultarPlacaCommand;
import com.erp.integrationhub.application.dto.command.ConsultarRucCommand;
import com.erp.integrationhub.application.dto.result.PlacaResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.ConsultarPlacaUseCase;
import com.erp.integrationhub.application.usecase.ConsultarRucUseCase;
import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.model.ConsultaRucResult;
import com.erp.integrationhub.domain.model.IntegrationAttempt;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public class IdentityValidationService implements ConsultarRucUseCase, ConsultarPlacaUseCase {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationService.class);

    private final ValidadorIdentidadPort validadorIdentidadPort;
    private final ValidadorVehicularPort validadorVehicularPort;
    private final IdentityCachePort identityCachePort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;
    private final UnitOfWork unitOfWork;

    public IdentityValidationService(
            ValidadorIdentidadPort validadorIdentidadPort,
            ValidadorVehicularPort validadorVehicularPort,
            IdentityCachePort identityCachePort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        this.validadorIdentidadPort = validadorIdentidadPort;
        this.validadorVehicularPort = validadorVehicularPort;
        this.identityCachePort = identityCachePort;
        this.attemptRepositoryPort = attemptRepositoryPort;
        this.unitOfWork = unitOfWork;
    }


    @Override
    public RucResult consultarRuc(ConsultarRucCommand command) {
        if (command.ruc() == null || command.ruc().trim().length() != 11) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos");
        }

        String ruc = command.ruc().trim();
        Optional<ConsultaRucResult> cached = identityCachePort != null ? identityCachePort.getRuc(ruc) : Optional.empty();
        if (cached.isPresent()) {
            log.debug("RUC {} obtenido de la caché (Redis / L1)", ruc);
            return toRucResult(cached.get(), true);
        }

        ConsultaRucResult result;
        try {
            result = validadorIdentidadPort.consultarRuc(ruc);
            if (result.esValido() && identityCachePort != null) {
                identityCachePort.putRuc(ruc, result);
            }
            logAttempt(command.tenantId(), ProviderType.CHEQUEA, "VALIDATE_RUC", ruc, null);
        } catch (Exception e) {
            log.warn("Fallo al validar RUC {} con proveedor externo: {}", ruc, e.getMessage());
            result = ConsultaRucResult.invalido(ruc);
            logAttempt(command.tenantId(), ProviderType.CHEQUEA, "VALIDATE_RUC", ruc, e.getMessage());
        }

        return toRucResult(result, false);
    }

    @Override
    public PlacaResult consultarPlaca(ConsultarPlacaCommand command) {
        if (command.placa() == null || command.placa().trim().isEmpty()) {
            throw new IllegalArgumentException("La placa vehicular es obligatoria");
        }

        String placa = command.placa().trim().toUpperCase().replace("-", "");
        Optional<ConsultaPlacaResult> cached = identityCachePort != null ? identityCachePort.getPlaca(placa) : Optional.empty();
        if (cached.isPresent()) {
            log.debug("Placa {} obtenida de la caché (Redis / L1)", placa);
            return toPlacaResult(cached.get(), true);
        }

        ConsultaPlacaResult result;
        try {
            result = validadorVehicularPort.consultarPlaca(placa);
            if (result.esValido() && identityCachePort != null) {
                identityCachePort.putPlaca(placa, result);
            }
            logAttempt(command.tenantId(), ProviderType.SUNARP, "VALIDATE_PLATE", placa, null);
        } catch (Exception e) {
            log.warn("Fallo al consultar placa {} con proveedor externo: {}", placa, e.getMessage());
            result = ConsultaPlacaResult.invalido(placa);
            logAttempt(command.tenantId(), ProviderType.SUNARP, "VALIDATE_PLATE", placa, e.getMessage());
        }

        return toPlacaResult(result, false);
    }

    private void logAttempt(UUID tenantId, ProviderType provider, String op, String referenceKey, String error) {
        UUID effectiveTenant = tenantId;
        UUID referenceId = (referenceKey != null && !referenceKey.isBlank())
                ? UUID.nameUUIDFromBytes(referenceKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : UUID.randomUUID();

        unitOfWork.runInTransaction(() -> attemptRepositoryPort.save(error == null
                ? IntegrationAttempt.ok(effectiveTenant, provider, op, referenceId, 1)
                : IntegrationAttempt.retry(effectiveTenant, provider, op, referenceId, 1, error)
        ));
    }

    private RucResult toRucResult(ConsultaRucResult r, boolean fromCache) {
        return new RucResult(
                r.ruc(),
                r.razonSocial(),
                r.estado(),
                r.condicion(),
                r.direccionFiscal(),
                r.departamento(),
                r.provincia(),
                r.distrito(),
                r.ubigeo(),
                r.esValido(),
                fromCache
        );
    }

    private PlacaResult toPlacaResult(ConsultaPlacaResult p, boolean fromCache) {
        return new PlacaResult(
                p.placa(),
                p.marca(),
                p.modelo(),
                p.vin(),
                p.numeroMotor(),
                p.anioFabricacion(),
                p.color(),
                p.combustible(),
                p.esValido(),
                fromCache
        );
    }
}
