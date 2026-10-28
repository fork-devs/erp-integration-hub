package com.erp.integrationhub.infrastructure.security;

import java.net.URI;
import java.util.List;

/**
 * Security policy for the JWKS endpoint URL.
 *
 * <p>The JWKS key set is downloaded via HTTP: without TLS encryption, network intermediaries
 * could tamper with keys and forge validly signed tokens, completely bypassing authentication.
 *
 * <p>The only exception is local development profiles where the mock or auth server runs locally.
 * Outside those profiles, the URL must use an encrypted scheme (HTTPS), or startup fails explicitly.
 */
public final class JwksUrlPolicy {

    /** Profiles permitting plaintext HTTP URLs. */
    private static final List<String> PERFILES_DE_DESARROLLO = List.of("dev", "local");

    private JwksUrlPolicy() {
    }

    /**
     * Validates the configured URL against the environment profile policy.
     *
     * @param jwksUrl configured JWKS URL
     * @param activeProfiles active Spring profiles
     */
    public static void verificar(String jwksUrl, List<String> activeProfiles) {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            throw new IllegalStateException(
                    "No hay URL del conjunto de claves configurada. Defina JWKS_URL con la direccion "
                    + "del endpoint /.well-known/jwks.json del servicio de autenticacion.");
        }

        String esquema;
        try {
            esquema = URI.create(jwksUrl.trim()).getScheme();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La URL del conjunto de claves no es valida: " + jwksUrl, e);
        }

        if ("https".equalsIgnoreCase(esquema)) {
            return;
        }

        boolean desarrolloLocal = activeProfiles != null
                && activeProfiles.stream().anyMatch(PERFILES_DE_DESARROLLO::contains);

        if (desarrolloLocal) {
            return;
        }

        throw new IllegalStateException(
                "La URL del conjunto de claves debe usar un esquema cifrado (https), y la configurada usa '"
                + esquema + "': " + jwksUrl + ". Sin cifrar, cualquiera que este en el camino puede sustituir "
                + "las claves y firmar tokens que el servicio aceptara. La unica excepcion es el perfil de "
                + "desarrollo local.");
    }
}
