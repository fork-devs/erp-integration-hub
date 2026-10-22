package com.erp.integrationhub.infrastructure.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Principal autenticado que llama al hub.
 *
 * <p>Se separan dos conceptos que antes estaban confundidos:
 *
 * <ul>
 *   <li><strong>Operar sobre un taller concreto</strong>
 *       ({@link #canOperateOnTenant}): un usuario de un taller solo opera
 *       sobre el suyo. Un servicio interno y el superadministrador de la
 *       plataforma tienen alcance transversal, porque su funcion lo
 *       requiere.</li>
 *   <li><strong>Administrar la plataforma</strong>
 *       ({@link #isPlatformAdmin()}): solo el superadministrador. La
 *       credencial compartida de servicio interno ya no concede este
 *       privilegio: antes receive el rol de administrador de plataforma y
 *       con ello la capacidad de listar los intentos y la cola de muertos de
 *       TODOS los talleres.</li>
 * </ul>
 *
 * <p>Ver docs/doc-estado-implementacion.md, hallazgos P1 6.50 y P1 6.53.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        String username,
        Set<String> roles,
        boolean isInternalService
) implements Serializable {

    public boolean isSuperAdmin() {
        return roles != null && roles.contains("ROLE_SUPER_ADMIN");
    }

    /**
     * Administrador de plataforma: puede consultar el alcance transversal de
     * los endpoints de administracion.
     */
    public boolean isPlatformAdmin() {
        return roles != null && (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPER_ADMIN"));
    }

    public boolean isAdmin() {
        return isPlatformAdmin();
    }

    /**
     * El principal puede operar sobre el taller solicitado.
     */
    public boolean canOperateOnTenant(UUID requestedTenantId) {
        if (requestedTenantId == null) {
            return false;
        }
        if (tenantId != null) {
            return tenantId.equals(requestedTenantId);
        }
        // Sin taller propio: el servicio interno y el superadministrador
        // tienen alcance transversal.
        return isInternalService || isSuperAdmin();
    }
}
