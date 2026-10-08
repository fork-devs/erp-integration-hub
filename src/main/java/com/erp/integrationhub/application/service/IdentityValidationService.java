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
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

public class IdentityValidationService implements ConsultarRucUseCase, ConsultarPlacaUseCase {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationService.class);

    private final ValidadorIdentidadPort validadorIdentidadPort;
    private final ValidadorVehicularPort validadorVehicularPort;
    private final IntegrationAttemptRepositoryPort attemptRepositoryPort;

    // Caché con TTL: RUC (30 días), Placas (7 días) según QA-6 / doc-microservices.md
    private final Cache<String, ConsultaRucResult> rucCache;
    private final Cache<String, ConsultaPlacaResult> placaCache;

    public IdentityValidationService(
            ValidadorIdentidadPort validadorIdentidadPort,
            ValidadorVehicularPort validadorVehicularPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort) {
        this.validadorIdentidadPort = validadorIdentidadPort;
        this.validadorVehicularPort = validadorVehicularPort;
        this.attemptRepositoryPort = attemptRepositoryPort;

        this.rucCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(30))
                .maximumSize(50000)
                .build();

        this.placaCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(7))
                .maximumSize(50000)
                .build();
    }

    @Override
    public RucResult consultarRuc(ConsultarRucCommand command) {
        if (command.ruc() == null || command.ruc().trim().length() != 11) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos");
        }

        String ruc = command.ruc().trim();
        ConsultaRucResult cached = rucCache.getIfPresent(ruc);
        if (cached != null) {
            log.debug("RUC {} encontrado en caché local", ruc);
            return toRucResult(cached, true);
        }

        ConsultaRucResult result;
        try {
            result = validadorIdentidadPort.consultarRuc(ruc);
            if (result.esValido()) {
                rucCache.put(ruc, result);
            }
            logAttempt(command.tenantId(), ProviderType.RENIEC_SUNAT, "VALIDATE_RUC", null);
        } catch (Exception e) {
            log.warn("Fallo al validar RUC {} con proveedor externo: {}", ruc, e.getMessage());
            result = ConsultaRucResult.invalido(ruc);
        }

        return toRucResult(result, false);
    }

    @Override
    public PlacaResult consultarPlaca(ConsultarPlacaCommand command) {
        if (command.placa() == null || command.placa().trim().isEmpty()) {
            throw new IllegalArgumentException("La placa vehicular es obligatoria");
        }

        String placa = command.placa().trim().toUpperCase().replace("-", "");
        ConsultaPlacaResult cached = placaCache.getIfPresent(placa);
        if (cached != null) {
            log.debug("Placa {} encontrada en caché local", placa);
            return toPlacaResult(cached, true);
        }

        ConsultaPlacaResult result;
        try {
            result = validadorVehicularPort.consultarPlaca(placa);
            if (result.esValido()) {
                placaCache.put(placa, result);
            }
            logAttempt(command.tenantId(), ProviderType.SUNARP, "VALIDATE_PLATE", null);
        } catch (Exception e) {
            log.warn("Fallo al consultar placa {} con proveedor externo: {}", placa, e.getMessage());
            result = ConsultaPlacaResult.invalido(placa);
        }

        return toPlacaResult(result, false);
    }

    private void logAttempt(UUID tenantId, ProviderType provider, String op, String error) {
        UUID effectiveTenant = tenantId != null ? tenantId : UUID.randomUUID();
        attemptRepositoryPort.save(error == null
                ? IntegrationAttempt.ok(effectiveTenant, provider, op, UUID.randomUUID(), 1)
                : IntegrationAttempt.retry(effectiveTenant, provider, op, UUID.randomUUID(), 1, error)
        );
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
