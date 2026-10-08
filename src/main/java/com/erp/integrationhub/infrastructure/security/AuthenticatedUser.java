package com.erp.integrationhub.infrastructure.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

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

    public boolean isAdmin() {
        return roles != null && (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPER_ADMIN"));
    }

    public boolean canAccessTenant(UUID requestedTenantId) {
        if (isInternalService || isSuperAdmin()) {
            return true;
        }
        return tenantId != null && tenantId.equals(requestedTenantId);
    }
}
