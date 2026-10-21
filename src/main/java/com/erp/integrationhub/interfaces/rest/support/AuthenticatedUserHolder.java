package com.erp.integrationhub.interfaces.rest.support;

import com.erp.integrationhub.infrastructure.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso al principal autenticado de la peticion en curso.
 */
public final class AuthenticatedUserHolder {

    private AuthenticatedUserHolder() {
    }

    public static AuthenticatedUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
