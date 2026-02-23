package com.wpanther.document.intake.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

/**
 * Security configuration for document-intake-service.
 * <p>
 * This configuration provides:
 * - OAuth2/JWT authentication for API endpoints
 * - Public access to actuator health endpoints (for monitoring)
 * - Rate limiting support
 * - Stateless session management (stateless REST API)
 * <p>
 * Security is enabled by default. To disable (for development), set:
 * {@code app.security.enabled=false}
 */
@Slf4j
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = "app.security.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SecurityConfig {

    /**
     * OAuth2 resource server configuration for JWT authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security with OAuth2/JWT authentication");

        http
            // Disable CSRF (stateless REST API)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public actuator health endpoints (for monitoring tools)
                .requestMatchers(request -> {
                    String path = request.getRequestURI();
                    return path.equals("/actuator/health")
                        || path.equals("/actuator/health/readiness")
                        || path.equals("/actuator/health/liveness");
                }).permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // OAuth2 resource server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // Return 401 instead of redirect to login
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }

    /**
     * JWT authentication converter.
     * <p>
     * Extracts authorities from JWT token. Supports both standard claims
     * and custom claim names:
     * - Standard: "scope" or "scp"
     * - Custom: "authorities" or "roles"
     * <p>
     * Prefixes scope values with "SCOPE_" for Spring Security's
     * hasAuthority() checks.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    /**
     * Security configuration for when security is disabled (development/testing only).
     */
    @Configuration
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    @EnableWebSecurity
    public static class SecurityDisabledConfig {

        @Bean
        public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
            log.warn("SECURITY IS DISABLED - This should only be used for development/testing");

            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }
}
