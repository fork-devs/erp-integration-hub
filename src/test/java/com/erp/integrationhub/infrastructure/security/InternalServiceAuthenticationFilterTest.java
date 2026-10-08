package com.erp.integrationhub.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalServiceAuthenticationFilterTest {

    private InternalServiceAuthenticationFilter filter;
    private JwksKeyManager jwksKeyManager;
    private final String internalKey = "test_internal_key_123";
    private KeyPair keyPair;
    private final String keyId = "test-rsa-key-1";

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        this.keyPair = kpg.generateKeyPair();

        this.jwksKeyManager = new JwksKeyManager("http://localhost:8081/.well-known/jwks.json");
        this.jwksKeyManager.registerManualKey(keyId, (RSAPublicKey) keyPair.getPublic());

        this.filter = new InternalServiceAuthenticationFilter(internalKey, jwksKeyManager);
        SecurityContextHolder.clearContext();
    }

    private String createValidSignedJwt(UUID tenantId, List<String> roles, Date expiration) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", roles)
                .expirationTime(expiration)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("Should authenticate request with valid X-Internal-Service-Key")
    void doFilter_WithValidInternalServiceKey_ShouldAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("X-Internal-Service-Key", internalKey);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should reject request without authentication headers on protected endpoint")
    void doFilter_WithoutAuth_ShouldReturn401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should allow request when RS256 JWT signature is valid and tenant_id matches X-Tenant-ID header")
    void doFilter_WithValidSignedJwtAndMatchingTenantId_ShouldAuthenticate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Date expiration = new Date(System.currentTimeMillis() + 60000);
        String token = createValidSignedJwt(tenantId, List.of("ROLE_TENANT_ADMIN"), expiration);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-ID", tenantId.toString());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject unsigned (PlainJWT / alg=none) token with 401 Unauthorized")
    void doFilter_WithUnsignedToken_ShouldReturn401Unauthorized() throws Exception {
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of("ROLE_SUPER_ADMIN"))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        String plainToken = new PlainJWT(claims).serialize();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + plainToken);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should reject token signed with untrusted / forged RSA private key")
    void doFilter_WithForgedSignature_ShouldReturn401Unauthorized() throws Exception {
        // Generar clave de atacante
        KeyPairGenerator attackerKpg = KeyPairGenerator.getInstance("RSA");
        attackerKpg.initialize(2048);
        KeyPair attackerKeyPair = attackerKpg.generateKeyPair();

        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of("ROLE_SUPER_ADMIN"))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyId) // Usa el keyId legítimo pero firmado con clave falsa
                .build();

        SignedJWT forgedJWT = new SignedJWT(header, claims);
        forgedJWT.sign(new RSASSASigner((RSAPrivateKey) attackerKeyPair.getPrivate()));
        String forgedToken = forgedJWT.serialize();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + forgedToken);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("QA-4: Should reject with 403 Forbidden when JWT tenant_id DOES NOT match X-Tenant-ID header")
    void doFilter_WithMismatchedTenantId_ShouldReturn403Forbidden() throws Exception {
        UUID tokenTenantId = UUID.randomUUID();
        UUID forgedTenantId = UUID.randomUUID();
        Date expiration = new Date(System.currentTimeMillis() + 60000);

        String token = createValidSignedJwt(tokenTenantId, List.of("ROLE_TENANT_ADMIN"), expiration);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-ID", forgedTenantId.toString()); // Intento de spoofing

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("Should reject expired JWT token with 401 Unauthorized")
    void doFilter_WithExpiredToken_ShouldReturn401Unauthorized() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Date expiredDate = new Date(System.currentTimeMillis() - 60000); // 1 minuto en el pasado

        String token = createValidSignedJwt(tenantId, List.of("ROLE_USER"), expiredDate);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }
}
