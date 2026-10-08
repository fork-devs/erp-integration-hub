package com.erp.integrationhub.infrastructure.config;

import com.erp.integrationhub.infrastructure.security.InternalServiceAuthenticationFilter;
import com.erp.integrationhub.infrastructure.security.JwksKeyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${erp.security.internal-service-key:erp_internal_secret_service_key_2026}")
    private String internalServiceKey;

    @Bean
    public InternalServiceAuthenticationFilter internalServiceAuthenticationFilter(JwksKeyManager jwksKeyManager) {
        return new InternalServiceAuthenticationFilter(internalServiceKey, jwksKeyManager);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, InternalServiceAuthenticationFilter filter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Public Documentation and Health checks
                .requestMatchers(
                    "/actuator/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                // Webhooks protected by HMAC cryptographic signature validation
                .requestMatchers(
                    "/api/v1/integrations/webhooks/**"
                ).permitAll()
                // Admin endpoints require ADMIN or INTERNAL_SERVICE
                .requestMatchers(
                    "/api/v1/integrations/admin/**"
                ).hasAnyRole("ADMIN", "SUPER_ADMIN", "INTERNAL_SERVICE")
                // All other integration endpoints require valid authenticated JWT or Internal Key
                .requestMatchers(
                    "/api/v1/integrations/**"
                ).authenticated()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
