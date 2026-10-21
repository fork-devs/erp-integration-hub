package com.erp.integrationhub.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthenticationFilter.class);
    private static final long CLOCK_SKEW_TOLERANCE_MS = 60000; // 60s tolerancia de reloj

    private final String internalServiceKey;
    private final JwksKeyManager jwksKeyManager;

    public InternalServiceAuthenticationFilter(String internalServiceKey, JwksKeyManager jwksKeyManager) {
        String resolvedKey = SecretResolver.resolveSecret(internalServiceKey);
        this.internalServiceKey = (resolvedKey != null && !resolvedKey.isBlank()) ? resolvedKey.trim() : null;
        this.jwksKeyManager = jwksKeyManager;

        if (this.internalServiceKey == null) {
            log.warn("ADVERTENCIA DE SEGURIDAD: INTERNAL_SERVICE_KEY no configurada. El bypass de servicios internos permanecerá deshabilitado.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/v1/integrations/webhooks");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String internalKeyHeader = request.getHeader("X-Internal-Service-Key");
        String tenantIdHeader = request.getHeader("X-Tenant-ID");

        AuthenticatedUser authenticatedUser = null;

        // 1. Validar llamada interna de microservicio hermano con comparación timing-safe y principio de mínimo privilegio
        if (internalKeyHeader != null && internalServiceKey != null) {
            byte[] expectedBytes = internalServiceKey.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = internalKeyHeader.trim().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
                // La credencial compartida identifica al servicio, no a un
                // administrador de plataforma. Antes concedia tambien
                // ROLE_ADMIN, con lo que podia listar los intentos y la cola
                // de muertos de TODOS los talleres. El alcance transversal
                // para operar sobre un taller concreto se mantiene, que es lo
                // que el servicio necesita, pero la administracion de la
                // plataforma queda reservada al superadministrador.
                authenticatedUser = new AuthenticatedUser(
                        UUID.randomUUID(),
                        null,
                        "internal-service",
                        Set.of("ROLE_INTERNAL_SERVICE"),
                        true
                );
            }
        }

        // 2. Validar Bearer JWT Token con verificación criptográfica RS256 contra JWKS
        if (authenticatedUser == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                // Parsear estrictamente como SignedJWT (rechaza plain tokens o alg=none)
                SignedJWT signedJWT = SignedJWT.parse(token);

                // Validar algoritmo RSA
                if (signedJWT.getHeader().getAlgorithm() == null ||
                        !JWSAlgorithm.Family.RSA.contains(signedJWT.getHeader().getAlgorithm())) {
                    log.warn("Rechazando token con algoritmo no permitido: {}", signedJWT.getHeader().getAlgorithm());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Algoritmo de firma de token no permitido o inseguro");
                    return;
                }

                // Obtener clave pública RSA desde JWKS
                String keyId = signedJWT.getHeader().getKeyID();
                RSAPublicKey publicKey = jwksKeyManager != null ? jwksKeyManager.getPublicKey(keyId) : null;
                if (publicKey == null) {
                    log.warn("No se encontró clave pública RSA en JWKS para kid: {}", keyId);
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "No se pudo verificar la firma del token: clave pública no disponible");
                    return;
                }

                // Verificar firma criptográfica
                RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
                if (!signedJWT.verify(verifier)) {
                    log.warn("Firma criptográfica inválida en token JWT para request {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Firma criptográfica del token inválida");
                    return;
                }

                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                long now = System.currentTimeMillis();

                // 2.1 Validación estricta y obligatoria de tiempo de expiración (exp)
                Date expirationTime = claims.getExpirationTime();
                if (expirationTime == null) {
                    log.warn("Token JWT rechazado: no contiene el claim obligatorio 'exp'");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT debe contener el claim obligatorio de expiración 'exp'");
                    return;
                }
                if (expirationTime.before(new Date(now))) {
                    log.warn("JWT Token expirado para request {}", request.getRequestURI());
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT ha expirado");
                    return;
                }

                // 2.2 Validación de Not-Before (nbf) si está presente
                Date notBeforeTime = claims.getNotBeforeTime();
                if (notBeforeTime != null && notBeforeTime.after(new Date(now + CLOCK_SKEW_TOLERANCE_MS))) {
                    log.warn("JWT Token aún no es válido según claim 'nbf'");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT aún no es válido (nbf futuro)");
                    return;
                }

                // 2.3 Validación de Issued-At (iat) si está presente
                Date issueTime = claims.getIssueTime();
                if (issueTime != null && issueTime.after(new Date(now + CLOCK_SKEW_TOLERANCE_MS))) {
                    log.warn("JWT Token emitido con fecha futura inválida según claim 'iat'");
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT emitido con fecha futura inválida (iat)");
                    return;
                }

                String sub = claims.getSubject();
                UUID userId = null;
                try {
                    if (sub != null) userId = UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {}

                String tenantClaim = (String) claims.getClaim("tenant_id");
                UUID tenantId = null;
                try {
                    if (tenantClaim != null) tenantId = UUID.fromString(tenantClaim);
                } catch (IllegalArgumentException ignored) {}

                Set<String> roles = new HashSet<>();
                Object rolesObj = claims.getClaim("roles");
                if (rolesObj instanceof List<?> list) {
                    for (Object r : list) {
                        if (r != null) {
                            String roleStr = r.toString();
                            roles.add(roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr);
                        }
                    }
                } else if (claims.getStringClaim("role") != null) {
                    String singleRole = claims.getStringClaim("role");
                    roles.add(singleRole.startsWith("ROLE_") ? singleRole : "ROLE_" + singleRole);
                } else {
                    roles.add("ROLE_USER");
                }

                authenticatedUser = new AuthenticatedUser(userId, tenantId, sub, roles, false);

            } catch (Exception e) {
                log.warn("Error al procesar y verificar Bearer JWT token: {}", e.getMessage());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT inválido o malformado: " + e.getMessage());
                return;
            }
        }

        // 3. Si no hay autenticación válida, denegar acceso a endpoints protegidos
        if (authenticatedUser == null) {
            log.warn("Intento de acceso no autenticado a {}", request.getRequestURI());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Autenticación requerida para acceder a este servicio");
            return;
        }

        // 4. Validación estricta de aislamiento Multi-Tenant (QA-4)
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
                UUID requestedTenantId = UUID.fromString(tenantIdHeader.trim());
                if (!authenticatedUser.canOperateOnTenant(requestedTenantId)) {
                    log.warn("Violación de aislamiento multi-tenant: Usuario con tenant {} intentó acceder al tenant {}",
                            authenticatedUser.tenantId(), requestedTenantId);
                    sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                            "Aislamiento Multi-Tenant: Acceso denegado al tenant especificado en X-Tenant-ID");
                    return;
                }
            } catch (IllegalArgumentException e) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "X-Tenant-ID debe ser un UUID válido");
                return;
            }
        }

        // 5. Establecer Authentication en SecurityContext
        List<SimpleGrantedAuthority> authorities = authenticatedUser.roles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                authenticatedUser,
                null,
                authorities
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        String json = String.format("""
                {
                    "type": "https://erp-automotive.pe/errors/security",
                    "title": "%s",
                    "status": %d,
                    "detail": "%s",
                    "timestamp": "%s"
                }
                """,
                status == 401 ? "Unauthorized" : (status == 403 ? "Forbidden" : "Bad Request"),
                status,
                message,
                java.time.Instant.now()
        );
        response.getWriter().write(json);
    }
}
