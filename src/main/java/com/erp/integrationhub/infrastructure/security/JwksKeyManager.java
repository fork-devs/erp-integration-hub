package com.erp.integrationhub.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class JwksKeyManager {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyManager.class);
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutos de caché

    private final String jwksUrl;
    private final AtomicReference<JWKSet> jwkSetCache = new AtomicReference<>(null);
    private final ConcurrentHashMap<String, RSAPublicKey> publicKeyMap = new ConcurrentHashMap<>();
    private volatile Instant lastFetchTime = Instant.EPOCH;

    public JwksKeyManager(@Value("${erp.security.jwks-url:http://localhost:8081/.well-known/jwks.json}") String jwksUrl) {
        this.jwksUrl = jwksUrl != null ? jwksUrl.trim() : "http://localhost:8081/.well-known/jwks.json";
    }

    public RSAPublicKey getPublicKey(String keyId) {
        if (keyId != null && publicKeyMap.containsKey(keyId)) {
            return publicKeyMap.get(keyId);
        }

        refreshKeysIfNecessary(false);

        if (keyId != null && publicKeyMap.containsKey(keyId)) {
            return publicKeyMap.get(keyId);
        }

        // Si no se encuentra, forzar refresco por si hubo rotación de claves en auth-service
        refreshKeysIfNecessary(true);
        return keyId != null ? publicKeyMap.get(keyId) : getAnyPublicKey();
    }

    public void registerManualKey(String keyId, RSAPublicKey publicKey) {
        if (keyId != null && publicKey != null) {
            publicKeyMap.put(keyId, publicKey);
        }
    }

    private synchronized void refreshKeysIfNecessary(boolean force) {
        Instant now = Instant.now();
        if (!force && jwkSetCache.get() != null && lastFetchTime.plusSeconds(CACHE_TTL_SECONDS).isAfter(now)) {
            return;
        }

        if (jwksUrl == null || jwksUrl.isBlank()) {
            log.warn("JWKS URL no configurada en erp-integration-hub");
            return;
        }

        try {
            log.info("Descargando JWKS para validación criptográfica desde {}", jwksUrl);
            JWKSet jwkSet = JWKSet.load(URI.create(jwksUrl).toURL());
            jwkSetCache.set(jwkSet);
            lastFetchTime = now;

            publicKeyMap.clear();
            for (JWK jwk : jwkSet.getKeys()) {
                if (jwk instanceof RSAKey rsaKey) {
                    try {
                        RSAPublicKey pubKey = rsaKey.toRSAPublicKey();
                        if (rsaKey.getKeyID() != null) {
                            publicKeyMap.put(rsaKey.getKeyID(), pubKey);
                        } else {
                            publicKeyMap.put("default", pubKey);
                        }
                    } catch (Exception e) {
                        log.error("Error al convertir clave RSA JWK a RSAPublicKey", e);
                    }
                }
            }
            log.info("JWKS actualizado correctamente. Claves RSA cargadas: {}", publicKeyMap.keySet());
        } catch (Exception e) {
            log.warn("No se pudo cargar JWKS desde auth-service (continuando con claves existentes si las hay): {}", e.getMessage());
        }
    }

    private RSAPublicKey getAnyPublicKey() {
        return publicKeyMap.values().stream().findFirst().orElse(null);
    }
}
