package com.erp.integrationhub.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for fetching and caching public keys from the authentication service JWKS endpoint.
 *
 * <p>Key security and operational properties:
 *
 * <ul>
 *   <li>Cache expiration is checked <strong>before</strong> returning a known key.
 *       Previously, cached keys were returned indefinitely without verification,
 *       allowing rotated keys to remain valid throughout the entire cache window.</li>
 *   <li>An unknown key ID is <strong>rejected</strong> without falling back to arbitrary keys:
 *       verifying against arbitrary keys is insecure.</li>
 *   <li>Key fetching enforces explicit connection and read timeouts.</li>
 *   <li>Download failures <strong>do not purge the cache</strong>: previously loaded valid keys
 *       are retained, and retry intervals are rate-limited to avoid thundering herd issues.</li>
 * </ul>
 */
public class JwksKeyManager {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyManager.class);

    /** Cache time-to-live following a successful download. */
    private static final long CACHE_TTL_SECONDS = 300;

    /** Interval between retries following a failed download. */
    private static final long RETRY_INTERVAL_SECONDS = 10;

    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int READ_TIMEOUT_MILLIS = 3000;

    private final String jwksUrl;
    private final ConcurrentHashMap<String, RSAPublicKey> publicKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RSAPublicKey> manualKeyMap = new ConcurrentHashMap<>();

    /** Timestamp of the last successful key set download. */
    private volatile Instant lastSuccessTime = Instant.EPOCH;

    /** Timestamp of the last download attempt (successful or failed). */
    private volatile Instant lastAttemptTime = Instant.EPOCH;

    /** Flag indicating whether the last download attempt failed. */
    private volatile boolean lastAttemptFailed = false;

    public JwksKeyManager(String jwksUrl, List<String> activeProfiles) {
        JwksUrlPolicy.verificar(jwksUrl, activeProfiles);
        this.jwksUrl = jwksUrl == null ? "" : jwksUrl.trim();
    }

    public RSAPublicKey getPublicKey(String keyId) {
        if (keyId != null) {
            RSAPublicKey manual = manualKeyMap.get(keyId);
            if (manual != null) {
                return manual;
            }
        }

        // Verify cache freshness BEFORE returning a known key:
        // otherwise a rotated key would remain valid throughout the cache window.
        refrescarSiProcede(false);

        if (keyId != null) {
            RSAPublicKey clave = publicKeyMap.get(keyId);
            if (clave != null) {
                return clave;
            }
        }

        // Unknown key identifier: trigger a forced refresh to account for recent key rotation.
        refrescarSiProcede(true);

        if (keyId != null) {
            return publicKeyMap.get(keyId);
        }

        // Without a key identifier, no key can be resolved. Verification against arbitrary keys is insecure.
        log.warn("Se ha presentado un token sin identificador de clave: se rechaza");
        return null;
    }

    public void registerManualKey(String keyId, RSAPublicKey publicKey) {
        if (keyId != null && publicKey != null) {
            manualKeyMap.put(keyId, publicKey);
            publicKeyMap.put(keyId, publicKey);
        }
    }

    /**
     * Refreshes the key set from the remote JWKS endpoint unless the cache remains valid
     * or the retry interval after failure has not elapsed.
     */
    private synchronized void refrescarSiProcede(boolean force) {
        Instant now = Instant.now();

        if (lastAttemptFailed && lastAttemptTime.plusSeconds(RETRY_INTERVAL_SECONDS).isAfter(now)) {
            // The previous attempt failed: wait until the retry interval elapses.
            // This prevents repeated requests with unknown key IDs from overloading the identity provider.
            return;
        }

        if (!force && lastSuccessTime.plusSeconds(CACHE_TTL_SECONDS).isAfter(now)) {
            return;
        }

        lastAttemptTime = now;

        try {
            if (jwksUrl.isEmpty()) {
                log.warn("JWKS URL no configurada");
                lastAttemptFailed = true;
                return;
            }

            log.info("Descargando JWKS desde {}", jwksUrl);
            JWKSet jwkSet = descargar();
            ConcurrentHashMap<String, RSAPublicKey> claves = new ConcurrentHashMap<>();

            for (JWK jwk : jwkSet.getKeys()) {
                if (jwk instanceof RSAKey rsaKey) {
                    try {
                        RSAPublicKey pubKey = rsaKey.toRSAPublicKey();
                        claves.put(rsaKey.getKeyID() != null ? rsaKey.getKeyID() : "default", pubKey);
                    } catch (Exception e) {
                        log.error("Error al convertir clave RSA JWK a RSAPublicKey", e);
                    }
                }
            }

            // The active key map is only replaced upon a successful fetch:
            // a transient failure preserves existing keys.
            publicKeyMap.clear();
            publicKeyMap.putAll(claves);
            lastSuccessTime = now;
            lastAttemptFailed = false;

            log.info("JWKS actualizado correctamente. Claves cargadas: {}", publicKeyMap.keySet());
        } catch (Exception e) {
            lastAttemptFailed = true;
            log.warn("No se pudo cargar JWKS desde auth-service (continuando con claves existentes si las hay): {}",
                    e.getMessage());
        }
    }

    private JWKSet descargar() throws Exception {
        URL url = URI.create(jwksUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("GET");
        try (InputStream input = connection.getInputStream()) {
            return JWKSet.load(input);
        } finally {
            connection.disconnect();
        }
    }
}
