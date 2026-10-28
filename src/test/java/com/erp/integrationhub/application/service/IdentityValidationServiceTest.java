package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.QueryPlateCommand;
import com.erp.integrationhub.application.dto.command.QueryRucCommand;
import com.erp.integrationhub.application.dto.result.PlateResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import com.erp.integrationhub.infrastructure.adapter.cache.RedisIdentityCacheAdapter;
import com.erp.integrationhub.support.DirectUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityValidationServiceTest {

    @Mock
    private IdentityValidatorPort identityValidatorPort;

    @Mock
    private VehicleValidatorPort vehicleValidatorPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    private IdentityValidationService identityValidationService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IdentityCachePort identityCachePort = new RedisIdentityCacheAdapter(null);
        identityValidationService = new IdentityValidationService(
                identityValidatorPort,
                vehicleValidatorPort,
                identityCachePort,
                attemptRepositoryPort,
                DirectUnitOfWork.INSTANCE
        );
    }

    @Test
    @DisplayName("Should query RUC from external provider on cache miss and from cache on second call")
    void queryRuc_CachingVerification() {
        // Given
        String ruc = "20601234567";
        QueryRucCommand command = new QueryRucCommand(tenantId, ruc);

        QueryRucResult mockRucResult = new QueryRucResult(
                ruc,
                "TALLER MECANICO RAPIDO SAC",
                "ACTIVO",
                "HABIDO",
                "Av. Nicolas Arriola 123",
                "LIMA",
                "LIMA",
                "LA VICTORIA",
                "150115",
                true
        );
        when(identityValidatorPort.queryRuc(ruc)).thenReturn(mockRucResult);

        // When 1: First call (miss)
        RucResult result1 = identityValidationService.queryRuc(command);

        // Then 1
        assertNotNull(result1);
        assertTrue(result1.valid());
        assertFalse(result1.fromCache());
        assertEquals("TALLER MECANICO RAPIDO SAC", result1.legalName());

        // When 2: Second call (hit in Caffeine cache)
        RucResult result2 = identityValidationService.queryRuc(command);

        // Then 2
        assertNotNull(result2);
        assertTrue(result2.valid());
        assertTrue(result2.fromCache());
        assertEquals("TALLER MECANICO RAPIDO SAC", result2.legalName());

        // External port should only be called once!
        verify(identityValidatorPort, times(1)).queryRuc(ruc);
    }

    @Test
    @DisplayName("Should query Plate from external provider on cache miss and from cache on second call")
    void queryPlate_CachingVerification() {
        // Given
        String plate = "ABC123";
        QueryPlateCommand command = new QueryPlateCommand(tenantId, plate);

        QueryPlateResult mockPlateResult = new QueryPlateResult(
                plate,
                "TOYOTA",
                "HILUX",
                "8AJHA89201L001234",
                "1GD-FTV-12345",
                "2023",
                "BLANCO",
                "DIESEL",
                true
        );
        when(vehicleValidatorPort.queryPlate(plate)).thenReturn(mockPlateResult);

        // When 1: First call (miss)
        PlateResult result1 = identityValidationService.queryPlate(command);

        // Then 1
        assertNotNull(result1);
        assertTrue(result1.valid());
        assertFalse(result1.fromCache());
        assertEquals("TOYOTA", result1.brand());

        // When 2: Second call (hit in Caffeine cache)
        PlateResult result2 = identityValidationService.queryPlate(command);

        // Then 2
        assertNotNull(result2);
        assertTrue(result2.valid());
        assertTrue(result2.fromCache());
        assertEquals("TOYOTA", result2.brand());

        // External port should only be called once!
        verify(vehicleValidatorPort, times(1)).queryPlate(plate);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when RUC is not 11 digits")
    void queryRuc_WhenInvalidLength_ThrowsException() {
        QueryRucCommand command = new QueryRucCommand(tenantId, "12345");
        assertThrows(IllegalArgumentException.class, () -> identityValidationService.queryRuc(command));
    }
}
