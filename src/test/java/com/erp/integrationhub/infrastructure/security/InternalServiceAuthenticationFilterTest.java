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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalServiceAuthenticationFilterTest {

    private InternalServiceAuthenticationFilter filter;
    private JwksKeyManager jwksKeyManager;
    private final String internalKey = "test_internal_key_123";
    private final String claveFleetService = "clave_de_fleet_service";
    private KeyPair keyPair;
    private final String keyId = "test-rsa-key-1";

    private org.springframework.mock.env.MockEnvironment entorno;

    @BeforeEach
    void setUp() throws Exception {
        entorno = new org.springframework.mock.env.MockEnvironment();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        this.keyPair = kpg.generateKeyPair();

        // El perfil de desarrollo local es el unico que permite la URL en
        // claro; el resto de las comprobaciones las hace el propio cliente.
        this.jwksKeyManager = new JwksKeyManager(
                "http://localhost:8081/.well-known/jwks.json", List.of("dev"));
        this.jwksKeyManager.registerManualKey(keyId, (RSAPublicKey) keyPair.getPublic());

        // La ventana de compatibilidad de la clave compartida esta ABIERTA en
        // esta prueba: es el estado transitorio en el que los servicios van
        // adoptando su clave propia.
        this.filter = new InternalServiceAuthenticationFilter(internalKey,
                java.util.Map.of("erp-fleet-service", claveFleetService), true, jwksKeyManager, entorno);
        SecurityContextHolder.clearContext();
    }

    private String createValidSignedJwt(UUID tenantId, List<String> roles, Date expiration) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId != null ? tenantId.toString() : null)
                .claim("roles", roles)
                .issueTime(new Date());

        if (expiration != null) {
            builder.expirationTime(expiration);
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, builder.build());
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
    @DisplayName("Un servicio se autentica con SU clave y recibe SU rol")
    void unServicioSeAutenticaConSuClaveYRecibeSuRol() throws Exception {
        org.springframework.mock.env.MockEnvironment entorno = new org.springframework.mock.env.MockEnvironment();
        entorno.setProperty("erp.security.internal-service.roles.erp-fleet-service", "ROLE_FLEET");

        InternalServiceAuthenticationFilter filtro = new InternalServiceAuthenticationFilter(
                internalKey, java.util.Map.of("erp-fleet-service", claveFleetService), false,
                jwksKeyManager, entorno);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/validate/ruc/20601030013");
        request.addHeader("X-Internal-Service-Key", claveFleetService);
        request.addHeader("X-Internal-Service-Name", "erp-fleet-service");

        MockHttpServletResponse response = new MockHttpServletResponse();
        // El filtro limpia el contexto al terminar, de modo que el principal se
        // captura DURANTE la cadena.
        java.util.concurrent.atomic.AtomicReference<org.springframework.security.core.Authentication>
                capturado = new java.util.concurrent.atomic.AtomicReference<>();
        FilterChain filterChain = mock(FilterChain.class);
        org.mockito.Mockito.doAnswer(invocacion -> {
            capturado.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filtro.doFilter(request, response, filterChain);

        var principal = capturado.get();
        assertNotNull(principal, "El servicio se autentica con su propia clave");
        assertTrue(principal.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_FLEET")),
                "El servicio recibe el rol que se le ha configurado");
        assertTrue(principal.toString().contains("erp-fleet-service"),
                "El principal identifica al servicio que llama");
    }

    @Test
    @DisplayName("Un servicio NO puede declarar el nombre de otro con su propia clave")
    void unServicioNoPuedeDeclararElNombreDeOtro() throws Exception {
        org.springframework.mock.env.MockEnvironment entorno = new org.springframework.mock.env.MockEnvironment();
        entorno.setProperty("erp.security.internal-service.roles.erp-billing-service", "ROLE_BILLING");

        InternalServiceAuthenticationFilter filtro = new InternalServiceAuthenticationFilter(
                internalKey, java.util.Map.of("erp-fleet-service", claveFleetService), false,
                jwksKeyManager, entorno);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("X-Internal-Service-Key", claveFleetService);
        request.addHeader("X-Internal-Service-Name", "erp-billing-service");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filtro.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(),
                "La clave de un servicio no habilita a otro: sin esto el rol acotado seria decoracion");
    }

    @Test
    @DisplayName("Con la ventana cerrada, la clave compartida no autentica a nadie")
    void conLaVentanaCerradaLaClaveCompartidaNoAutentica() throws Exception {
        InternalServiceAuthenticationFilter filtro = new InternalServiceAuthenticationFilter(
                internalKey, java.util.Map.of("erp-fleet-service", claveFleetService), false,
                jwksKeyManager, entorno);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("X-Internal-Service-Key", internalKey);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filtro.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
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
                .keyID(keyId)
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
        request.addHeader("X-Tenant-ID", forgedTenantId.toString());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("Should reject expired JWT token with 401 Unauthorized")
    void doFilter_WithExpiredToken_ShouldReturn401Unauthorized() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Date expiredDate = new Date(System.currentTimeMillis() - 60000);

        String token = createValidSignedJwt(tenantId, List.of("ROLE_USER"), expiredDate);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should reject JWT token WITHOUT mandatory exp claim with 401 Unauthorized")
    void doFilter_WithoutExpClaim_ShouldReturn401Unauthorized() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = createValidSignedJwt(tenantId, List.of("ROLE_USER"), null); // No exp

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("Should reject JWT token with future nbf claim with 401 Unauthorized")
    void doFilter_WithFutureNbfClaim_ShouldReturn401Unauthorized() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Date futureDate = new Date(System.currentTimeMillis() + 300000); // 5 min in future

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of("ROLE_USER"))
                .notBeforeTime(futureDate)
                .expirationTime(new Date(System.currentTimeMillis() + 600000))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        String token = signedJWT.serialize();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }
}
