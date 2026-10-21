package com.erp.integrationhub.support;

import com.erp.integrationhub.application.dto.command.EmitirComprobanteCommand;
import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.service.ElectronicInvoicingService;
import com.erp.integrationhub.domain.model.ComprobanteTipo;
import com.erp.integrationhub.domain.model.ItemComprobante;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La anulacion: el ticket de comunicacion y la reprogramacion del rechazo
 * transitorio.
 *
 * <p>La autoridad fiscal exige el ticket de comunicacion del comprobante en
 * la solicitud de anulacion. Sin el, la solicitud se rechaza.
 *
 * <p>Y un rechazo TRANSITORIO (el proveedor no esta disponible) no es un
 * rechazo definitivo: la anulacion se reprograma, porque el comprobante
 * sigue sin anular y el taller tiene que poder reintentarlo.
 */
class VoidEmissionTest extends AbstractPostgresIntegrationTest {

    private static final UUID TALLER = UUID.fromString("cc11cc22-cc33-cc44-cc55-cc66cc77cc88");

    private EmisorTributarioPort emisorTributarioPort;

    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    private RetryQueueRepositoryPort retryQueueRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    private ElectronicInvoicingService servicio;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TALLER);
        // El emisor es un doble: el real es un bean de Spring, y llamarlo
        // dentro de when() ejecutaria la llamada de verdad.
        emisorTributarioPort = mock(EmisorTributarioPort.class);
        attemptRepositoryPort = mock(IntegrationAttemptRepositoryPort.class);
        idempotencyRepositoryPort = mock(IdempotencyRepositoryPort.class);
        retryQueueRepositoryPort = mock(RetryQueueRepositoryPort.class);
        servicio = new ElectronicInvoicingService(
                emisorTributarioPort,
                idempotencyRepositoryPort,
                attemptRepositoryPort,
                retryQueueRepositoryPort,
                unitOfWork,
                mock(com.erp.integrationhub.domain.port.VoucherNumberingPort.class));
    }

    @Test
    @DisplayName("Una anulacion rechazada de forma transitoria se reprograma, no se devuelve como definitiva")
    void unaAnulacionTransitoriaSeReprograma() {
        when(emisorTributarioPort.anularComprobante(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new com.erp.integrationhub.domain.exception.ProviderUnavailableException(
                        "El proveedor no esta disponible"));

        ComprobanteResult resultado = servicio.anularComprobante(
                TALLER, UUID.randomUUID(), "FACTURA", "F001", 123, "Error de emision");

        assertTrue(resultado.exitoso(),
                "Una anulacion reprogramada no es un rechazo definitivo: el comprobante sigue sin anular, "
                        + "pero el taller puede reintentarlo");
        assertEquals("PENDIENTE_SUNAT", resultado.estadoSunat());

        verify(retryQueueRepositoryPort).save(any());
        verify(attemptRepositoryPort).save(any());
    }

    @Test
    @DisplayName("Una anulacion rechazada de forma definitiva se devuelve al cliente y no se reprograma")
    void unaAnulacionDefinitivaNoSeReprograma() {
        when(emisorTributarioPort.anularComprobante(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(ResultadoDeclaracion.rechazado("2335", "El comprobante no se puede anular"));

        ComprobanteResult resultado = servicio.anularComprobante(
                TALLER, UUID.randomUUID(), "FACTURA", "F001", 123, "Error de emision");

        assertTrue(!resultado.exitoso(), "Un rechazo definitivo se devuelve al cliente");
        assertEquals("RECHAZADO", resultado.estadoSunat());
        assertEquals("2335", resultado.codigoRespuestaSunat(),
                "El codigo del proveedor se conserva en la respuesta");

        verify(retryQueueRepositoryPort, never()).save(any());
    }
}
