package com.erp.integrationhub.support;

import com.erp.integrationhub.infrastructure.security.InternalServiceAuthenticationFilter;
import com.erp.integrationhub.infrastructure.security.TenantRateLimiter;
import com.erp.integrationhub.infrastructure.security.JwksKeyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The internal identity is per service, and the identity queries are rate
 * limited per tenant.
 *
 * <p>See docs/doc-estado-implementacion.md, findings P1 6.49 and P1 6.50.
 */
class InternalIdentityAndRateLimitTest {

    private static final String CLAVE = "clave-de-servicio-interno";

    @Test
    @DisplayName("Each service gets only the roles configured for it")
    void cadaServicioRecibeSoloLosRolesQueLeCorresponden() throws Exception {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setProperty("erp.security.internal-service.roles.erp-billing-service",
                "ADMIN, SUPER_ADMIN");
        entorno.setProperty("erp.security.internal-service.roles.erp-work-order-service",
                "ASESOR");

        InternalServiceAuthenticationFilter filtro = new InternalServiceAuthenticationFilter(
                CLAVE, java.util.Map.of(), true, jwksKeyManager(), entorno);

        var admin = principalDe(filtro, "erp-billing-service");
        assertTrue(admin.roles().contains("ROLE_ADMIN"),
                "El servicio de facturacion tiene el rol que se le/configurado");
        assertTrue(admin.roles().contains("ROLE_SUPER_ADMIN"));

        var workOrder = principalDe(filtro, "erp-work-order-service");
        assertTrue(workOrder.roles().contains("ROLE_ASESOR"));
        assertFalse(workOrder.roles().contains("ROLE_ADMIN"),
                "El servicio de ordenes de taller NO puede administrar la plataforma: "
                        + "su rol no se le ha configurado");
    }

    @Test
    @DisplayName("A service without configured roles gets only the cross-cutting role")
    void unServicioSinRolesConfiguradosRecibeSoloElRolTransversal() throws Exception {
        MockEnvironment entorno = new MockEnvironment();

        InternalServiceAuthenticationFilter filtro = new InternalServiceAuthenticationFilter(
                CLAVE, java.util.Map.of(), true, jwksKeyManager(), entorno);

        var principal = principalDe(filtro, "erp-servicio-desconocido");

        assertEquals(java.util.Set.of("ROLE_INTERNAL_SERVICE"), principal.roles(),
                "Sin roles configurados, el servicio recibe solo el rol transversal minimo");
    }

    @Test
    @DisplayName("The identity query limit is per tenant, and the wait time is informed")
    void elLimiteDeConsultasDeIdentidadEsPorTaller() {
        TenantRateLimiter limitador = new TenantRateLimiter(3, 60);
        UUID taller = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertTrue(limitador.intentar(taller), "Las tres primeras peticiones se admiten");
        }

        assertFalse(limitador.intentar(taller), "La cuarta excede el limite");
        assertTrue(limitador.segundosDeEspera(taller) > 0,
                "El cliente tiene que saber cuanto esperar");

        // Otro taller tiene su propia cuota.
        assertTrue(limitador.intentar(UUID.randomUUID()),
                "El limite es por taller: otro taller no se ve afectado");
    }

    private com.erp.integrationhub.infrastructure.security.AuthenticatedUser principalDe(
            InternalServiceAuthenticationFilter filtro, String nombreServicio) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/integrations/admin/attempts");
        request.addHeader("X-Internal-Service-Key", CLAVE);
        request.addHeader("X-Internal-Service-Name", nombreServicio);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // El principal vive en el contexto de seguridad mientras dura la
        // cadena, y el filtro lo limpia al terminar.
        java.util.concurrent.atomic.AtomicReference<Object> capturado =
                new java.util.concurrent.atomic.AtomicReference<>();
        filtro.doFilter(request, response, (req, res) -> {
            var authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal()
                    instanceof com.erp.integrationhub.infrastructure.security.AuthenticatedUser usuario) {
                capturado.set(usuario);
            }
        });

        assertNotNull(capturado.get(),
                "La credencial compartida tiene que autenticar al servicio");
        return (com.erp.integrationhub.infrastructure.security.AuthenticatedUser) capturado.get();
    }

    private JwksKeyManager jwksKeyManager() {
        return new JwksKeyManager("http://localhost:8081/.well-known/jwks.json", List.of("dev"));
    }
}
