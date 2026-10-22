package com.erp.integrationhub.application.service;

import com.erp.integrationhub.application.dto.command.ConsultarPlacaCommand;
import com.erp.integrationhub.application.dto.command.ConsultarRucCommand;
import com.erp.integrationhub.application.dto.result.PlacaResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.model.ConsultaRucResult;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import com.erp.integrationhub.infrastructure.adapter.cache.RedisIdentityCacheAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.erp.integrationhub.support.DirectUnitOfWork;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityValidationServiceTest {

    @Mock
    private ValidadorIdentidadPort validadorIdentidadPort;

    @Mock
    private ValidadorVehicularPort validadorVehicularPort;

    @Mock
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    private IdentityValidationService identityValidationService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        IdentityCachePort identityCachePort = new RedisIdentityCacheAdapter(null);
        identityValidationService = new IdentityValidationService(
                validadorIdentidadPort,
                validadorVehicularPort,
                identityCachePort,
                attemptRepositoryPort,
                DirectUnitOfWork.INSTANCE
        );
    }

    @Test
    @DisplayName("Should query RUC from external provider on cache miss and from cache on second call")
    void consultarRuc_CachingVerification() {
        // Given
        String ruc = "20601234567";
        ConsultarRucCommand command = new ConsultarRucCommand(tenantId, ruc);

        ConsultaRucResult mockRucResult = new ConsultaRucResult(
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
        when(validadorIdentidadPort.consultarRuc(ruc)).thenReturn(mockRucResult);

        // When 1: First call (miss)
        RucResult result1 = identityValidationService.consultarRuc(command);

        // Then 1
        assertNotNull(result1);
        assertTrue(result1.esValido());
        assertFalse(result1.fromCache());
        assertEquals("TALLER MECANICO RAPIDO SAC", result1.razonSocial());

        // When 2: Second call (hit in Caffeine cache)
        RucResult result2 = identityValidationService.consultarRuc(command);

        // Then 2
        assertNotNull(result2);
        assertTrue(result2.esValido());
        assertTrue(result2.fromCache());
        assertEquals("TALLER MECANICO RAPIDO SAC", result2.razonSocial());

        // External port should only be called once!
        verify(validadorIdentidadPort, times(1)).consultarRuc(ruc);
    }

    @Test
    @DisplayName("Should query Placa from external provider on cache miss and from cache on second call")
    void consultarPlaca_CachingVerification() {
        // Given
        String placa = "ABC123";
        ConsultarPlacaCommand command = new ConsultarPlacaCommand(tenantId, placa);

        ConsultaPlacaResult mockPlacaResult = new ConsultaPlacaResult(
                placa,
                "TOYOTA",
                "HILUX",
                "8AJHA89201L001234",
                "1GD-FTV-12345",
                "2023",
                "BLANCO",
                "DIESEL",
                true
        );
        when(validadorVehicularPort.consultarPlaca(placa)).thenReturn(mockPlacaResult);

        // When 1: First call (miss)
        PlacaResult result1 = identityValidationService.consultarPlaca(command);

        // Then 1
        assertNotNull(result1);
        assertTrue(result1.esValido());
        assertFalse(result1.fromCache());
        assertEquals("TOYOTA", result1.marca());

        // When 2: Second call (hit in Caffeine cache)
        PlacaResult result2 = identityValidationService.consultarPlaca(command);

        // Then 2
        assertNotNull(result2);
        assertTrue(result2.esValido());
        assertTrue(result2.fromCache());
        assertEquals("TOYOTA", result2.marca());

        // External port should only be called once!
        verify(validadorVehicularPort, times(1)).consultarPlaca(placa);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when RUC is not 11 digits")
    void consultarRuc_WhenInvalidLength_ThrowsException() {
        ConsultarRucCommand command = new ConsultarRucCommand(tenantId, "12345");
        assertThrows(IllegalArgumentException.class, () -> identityValidationService.consultarRuc(command));
    }
}
