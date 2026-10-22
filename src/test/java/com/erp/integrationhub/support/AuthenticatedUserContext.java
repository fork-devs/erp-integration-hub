package com.erp.integrationhub.support;

import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

/**
 * Principal autenticado para los tests de los controladores del hub.
 *
 * <p>El hub deriva el taller efectivo del principal autenticado, de modo que
 * los tests de controlador necesitan un principal en el contexto de
 * seguridad.
 */
public final class AuthenticatedUserContext {

    private AuthenticatedUserContext() {
    }

    public static void asTenant(UUID tenantId) {
        set(tenantId, Set.of("ROLE_ADMIN"));
    }

    public static void asPlatformAdmin(UUID tenantId) {
        set(tenantId, Set.of("ROLE_SUPER_ADMIN"));
    }

    public static void asInternalService() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(UUID.randomUUID(), null, "internal-service",
                                Set.of("ROLE_INTERNAL_SERVICE"), true),
                        null,
                        java.util.List.of()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void set(UUID tenantId, Set<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(UUID.randomUUID(), tenantId, "usuario", roles, false),
                        null,
                        roles.stream().map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(r)).toList()));
    }
}
