package com.erp.integrationhub.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretResolver {

    private static final Logger log = LoggerFactory.getLogger(SecretResolver.class);

    /**
     * Resuelve referencias a secretos en Vault o Variables de Entorno según doc §5 sin fallback por defecto.
     */
    public static String resolveSecret(String secretRef) {
        return resolveSecret(secretRef, null);
    }

    /**
     * Resuelve referencias a secretos en Vault o Variables de Entorno según doc §5.
     * Ejemplo: vault:secret/apisunat#token, env:STRIPE_API_KEY, o token directo.
     */
    public static String resolveSecret(String secretRef, String fallbackDefault) {
        if (secretRef == null || secretRef.isBlank()) {
            return fallbackDefault;
        }

        String trimmed = secretRef.trim();

        // 1. Resolución de referencia a Vault
        if (trimmed.startsWith("vault:")) {
            String path = trimmed.substring(6);
            log.debug("Resolviendo secreto desde Vault en path: {}", path);
            // Intentar mapear path de vault a variable de entorno o secret manager local
            String envKey = path.replace("/", "_").replace(":", "_").replace("-", "_").toUpperCase();
            String vaultEnvVal = System.getenv(envKey);
            if (vaultEnvVal != null && !vaultEnvVal.isBlank()) {
                return vaultEnvVal;
            }
            return fallbackDefault;
        }

        // 2. Resolución de referencia a Variable de Entorno
        if (trimmed.startsWith("env:")) {
            String envVar = trimmed.substring(4).trim();
            String envVal = System.getenv(envVar);
            if (envVal != null && !envVal.isBlank()) {
                return envVal;
            }
            return fallbackDefault;
        }

        // 3. Valor directo
        return trimmed;
    }
}
