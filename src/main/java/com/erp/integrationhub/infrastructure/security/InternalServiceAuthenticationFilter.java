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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.Collectors;

public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthenticationFilter.class);
    private static final long CLOCK_SKEW_TOLERANCE_MS = 60000; // 60s tolerancia de reloj

    private final String internalServiceKey;
    private final java.util.Map<String, String> clavesPorServicio;
    private final boolean claveCompartidaHabilitada;
    private final JwksKeyManager jwksKeyManager;
    private final org.springframework.core.env.Environment environment;

    public InternalServiceAuthenticationFilter(
            String internalServiceKey,
            java.util.Map<String, String> clavesPorServicio,
            boolean claveCompartidaHabilitada,
            JwksKeyManager jwksKeyManager,
            org.springframework.core.env.Environment environment) {
        String resolvedKey = SecretResolver.resolveSecret(internalServiceKey);
        this.internalServiceKey = (resolvedKey != null && !resolvedKey.isBlank()) ? resolvedKey.trim() : null;
        this.clavesPorServicio = clavesPorServicio == null ? java.util.Map.of() : clavesPorServicio;
        this.claveCompartidaHabilitada = claveCompartidaHabilitada;
        this.jwksKeyManager = jwksKeyManager;
        this.environment = environment;

        if (this.internalServiceKey == null) {
            log.warn("ADVERTENCIA DE SEGURIDAD: INTERNAL_SERVICE_KEY no configurada. El bypass de servicios internos permanecerá deshabilitado.");
        }
        if (this.claveCompartidaHabilitada) {
            log.warn("ADVERTENCIA DE SEGURIDAD: la clave interna COMPARTIDA sigue habilitada. "
                    + "Quien la posea puede declarar el nombre de otro servicio y heredar sus roles. "
                    + "Debe desactivarse cuando todos los servicios usen su propia clave.");
        }
    }

    /**
     * Clave que se espera para el servicio que declara la peticion.
     *
     * <p>La clave es POR SERVICIO: cada servicio tiene la suya, y con ella no
     * puede declarar el nombre de otro. Con una clave compartida, quien la
     * posea puede declararse el servicio que quiera y heredar sus roles, de
     * modo que el rol acotado no seria mas que decoracion.
     *
     * <p>La clave compartida solo se acepta mientras la ventana de
     * compatibilidad siga abierta, y un servicio que ya tiene la suya usa la
     * suya aunque la ventana siga abierta.
     */
    private String claveDe(String nombreServicio) {
        if (nombreServicio != null && !nombreServicio.isBlank()) {
            String propia = clavesPorServicio.get(nombreServicio.trim());
            if (propia != null && !propia.isBlank()) {
                return propia;
            }
        }
        return claveCompartidaHabilitada ? internalServiceKey : null;
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
        String nombreServicioDeclarado = request.getHeader("X-Internal-Service-Name");
        String claveEsperada = claveDe(nombreServicioDeclarado);

        if (internalKeyHeader != null && claveEsperada != null) {
            byte[] expectedBytes = claveEsperada.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = internalKeyHeader.trim().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedBytes, actualBytes)) {
                // La credencial compartida identifica al SERVICIO, no a un
                // administrador de plataforma. Antes concedia tambien
                // ROLE_ADMIN, con lo que podia listar los intentos y la cola
                // de muertos de TODOS los talleres. El alcance transversal
                // para operar sobre un taller concreto se mantiene, que es lo
                // que el servicio necesita, pero la administracion de la
                // plataforma queda reservada al superadministrador.
                //
                // Ademas, la identidad es POR SERVICIO: cada servicio
                // recibe solo los roles que su configuracion le concede. Con
                // un rol unico para todos, cualquier servicio con la
                // credencial podia hacer lo que hiciera cualquier otro.
                String nombreServicio = nombreServicioDeclarado;
                authenticatedUser = new AuthenticatedUser(
                        UUID.randomUUID(),
                        null,
                        nombreServicio != null && !nombreServicio.isBlank()
                                ? nombreServicio.trim()
                                : "internal-service",
                        rolesDe(nombreServicio),
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

                // El claim de roles es un ARRAY, con el mismo contrato que
                // usa el gateway. Un token sin roles NO recibe un rol por
                // defecto: conceder un privilegio que el token no declara
                // es un bypass de autorizacion.
                Set<String> roles = new HashSet<>();
                Object rolesObj = claims.getClaim("roles");
                if (rolesObj instanceof List<?> list) {
                    for (Object r : list) {
                        if (r != null) {
                            String roleStr = r.toString();
                            roles.add(roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr);
                        }
                    }
                }

                if (roles.isEmpty()) {
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "El token no declara ningun rol");
                    return;
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

    /**
     * Roles concedidos al servicio que llama.
     *
     * <p>Se configuran por servicio: {@code erp.security.internal-service.roles.<servicio>}. Un
     * servicio sin configuracion recibe solo el rol transversal minimo.
     */
    private Set<String> rolesDe(String nombreServicio) {
        if (nombreServicio == null || nombreServicio.isBlank()) {
            return Set.of("ROLE_INTERNAL_SERVICE");
        }
        String clave = "erp.security.internal-service.roles." + nombreServicio.trim();
        String configuracion = environment.getProperty(clave);
        if (configuracion == null || configuracion.isBlank()) {
            log.warn("El servicio interno {} no tiene roles configurados: recibe solo el rol transversal",
                    nombreServicio);
            return Set.of("ROLE_INTERNAL_SERVICE");
        }
        Set<String> roles = Arrays.stream(configuracion.split(","))
                .map(String::trim)
                .filter(rol -> !rol.isEmpty())
                .map(rol -> rol.startsWith("ROLE_") ? rol : "ROLE_" + rol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return roles.isEmpty() ? Set.of("ROLE_INTERNAL_SERVICE") : roles;
    }

}
