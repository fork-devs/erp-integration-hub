package com.erp.integrationhub.support;

import com.erp.integrationhub.application.service.WebhookProcessingService;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Webhooks are idempotent, time-boxed, and signed with their own key.
 *
 * <p>See docs/doc-estado-implementacion.md, findings P1 6.47, P1 6.48 and
 * P1 6.49.
 */
class WebhookIdempotencyTest extends AbstractPostgresIntegrationTest {

    private static final String CLAVE_DE_AVISOS = "clave-de-firma-de-avisos-propia";
    private static final String CLAVE_DEL_PROVEEDOR = "credencial-de-acceso-al-proveedor";

    @Autowired
    private ProcessedWebhookRepositoryPort processedWebhookRepositoryPort;

    @Autowired
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Autowired
    private UnitOfWork unitOfWork;

    private WebhookProcessingService servicio;
    private IntegrationAttemptRepositoryPort attemptRepositoryPort;

    @BeforeEach
    void setUp() {
        // El registro del aviso procesado escribe en la base de datos, con la
        // politica de aislamiento: hace falta un contexto de taller.
        com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder.setTenantId(
                UUID.fromString("b0b0b0b0-c1c1-d2d2-e3e3-f4f4f4f4f4f4"));
        attemptRepositoryPort = mock(IntegrationAttemptRepositoryPort.class);
        servicio = new WebhookProcessingService(
                "secreto-de-stripe",
                CLAVE_DEL_PROVEEDOR,
                attemptRepositoryPort,
                idempotencyRepositoryPort,
                processedWebhookRepositoryPort,
                CLAVE_DE_AVISOS,
                300,
                unitOfWork);
    }

    @Test
    @DisplayName("A re-delivery is detected as such and the effect is applied only once")
    void unaReentregaSeDetectaYElEfectoSeAplicaUnaSolaVez() {
        String payload = """
                {
                  "id": "%s",
                  "tenant_id": "%s",
                  "comprobante_id": "%s",
                  "estado": "ACEPTADA"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        String firma = firmaDe(payload, CLAVE_DE_AVISOS);

        assertTrue(servicio.processWebhook(ProviderType.APISUNAT, payload, firma),
                "El primer aviso se procesa");

        // La reentrega del mismo aviso: se detecta y no se aplica dos veces.
        assertTrue(servicio.processWebhook(ProviderType.APISUNAT, payload, firma),
                "La reentrega se acepta, pero no se aplica dos veces");
    }

    @Test
    @DisplayName("A webhook signed with the provider's access credential is rejected")
    void unAvisoFirmadoConLaCredencialDelProveedorSeRechaza() {
        String payload = """
                {
                  "id": "%s",
                  "tenant_id": "%s",
                  "comprobante_id": "%s",
                  "estado": "ACEPTADA"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        String firmaConLaCredencial = firmaDe(payload, CLAVE_DEL_PROVEEDOR);

        assertThrows(RuntimeException.class,
                () -> servicio.processWebhook(ProviderType.APISUNAT, payload, firmaConLaCredencial),
                "La credencial de acceso al proveedor no puede firmar avisos");
    }

    @Test
    @DisplayName("A webhook older than the time window is rejected")
    void unAvisoFueraDeLaVentanaTemporalSeRechaza() {
        String payload = """
                {
                  "id": "%s",
                  "timestamp": %d,
                  "tenant_id": "%s",
                  "comprobante_id": "%s",
                  "estado": "ACEPTADA"
                }
                """.formatted(UUID.randomUUID(),
                Instant.now().minusSeconds(3600).toEpochMilli(),
                UUID.randomUUID(),
                UUID.randomUUID());

        String firma = firmaDe(payload, CLAVE_DE_AVISOS);

        assertThrows(RuntimeException.class,
                () -> servicio.processWebhook(ProviderType.APISUNAT, payload, firma),
                "Un aviso fuera de la ventana temporal debe rechazarse, aunque su firma sea valida");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder.clear();
    }

    private String firmaDe(String payload, String clave) {
        try {
            return WebhookProcessingService.computeHmacSha256(payload, clave);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
