package com.erp.integrationhub.infrastructure.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalServiceAuthenticationFilterTest {

    private InternalServiceAuthenticationFilter filter;
    private final String internalKey = "test_internal_key_123";

    @BeforeEach
    void setUp() {
        filter = new InternalServiceAuthenticationFilter(internalKey);
        SecurityContextHolder.clearContext();
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
    @DisplayName("Should allow request when JWT tenant_id matches X-Tenant-ID header")
    void doFilter_WithMatchingTenantId_ShouldAuthenticate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId.toString())
                .claim("roles", List.of("ROLE_TENANT_ADMIN"))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        String token = new PlainJWT(claims).serialize();

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
    @DisplayName("QA-4: Should reject with 403 Forbidden when JWT tenant_id DOES NOT match X-Tenant-ID header")
    void doFilter_WithMismatchedTenantId_ShouldReturn403Forbidden() throws Exception {
        UUID tokenTenantId = UUID.randomUUID();
        UUID forgedTenantId = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenant_id", tokenTenantId.toString())
                .claim("roles", List.of("ROLE_TENANT_ADMIN"))
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();

        String token = new PlainJWT(claims).serialize();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/integrations/invoices/emit");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-ID", forgedTenantId.toString()); // Intento de spoofing

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }
}
