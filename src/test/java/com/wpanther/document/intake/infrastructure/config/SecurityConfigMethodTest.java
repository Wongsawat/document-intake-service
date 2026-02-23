package com.wpanther.document.intake.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecurityConfig method signatures.
 * Tests that the required methods exist and can be called.
 */
@DisplayName("SecurityConfig Method Tests")
class SecurityConfigMethodTest {

    @Test
    @DisplayName("SecurityConfig can be instantiated")
    void testSecurityConfigCanBeInstantiated() {
        SecurityConfig config = new SecurityConfig();
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("SecurityConfig has securityFilterChain method")
    void testSecurityFilterChainMethodExists() throws Exception {
        var method = SecurityConfig.class.getDeclaredMethod("securityFilterChain", org.springframework.security.config.annotation.web.builders.HttpSecurity.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType().getName()).isEqualTo("org.springframework.security.web.SecurityFilterChain");
    }

    @Test
    @DisplayName("SecurityConfig has jwtAuthenticationConverter method")
    void testJwtAuthenticationConverterMethodExists() throws Exception {
        var method = SecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");
        assertThat(method).isNotNull();
        assertThat(method.getReturnType().getName()).isEqualTo("org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter");
    }

    @Test
    @DisplayName("SecurityConfig class annotations")
    void testSecurityConfigAnnotations() {
        assertThat(SecurityConfig.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class)).isTrue();
        assertThat(SecurityConfig.class.isAnnotationPresent(org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class)).isTrue();
    }

    @Test
    @DisplayName("SecurityConfig inner classes")
    void testSecurityConfigInnerClasses() {
        Class<?>[] innerClasses = SecurityConfig.class.getDeclaredClasses();
        assertThat(innerClasses).isNotEmpty();

        boolean hasSecurityDisabledConfig = false;
        for (Class<?> innerClass : innerClasses) {
            if (innerClass.getSimpleName().equals("SecurityDisabledConfig")) {
                hasSecurityDisabledConfig = true;
                break;
            }
        }
        assertThat(hasSecurityDisabledConfig).isTrue();
    }

    @Test
    @DisplayName("JwtGrantedAuthoritiesConverter can be created")
    void testJwtGrantedAuthoritiesConverterCanBeCreated() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        assertThat(converter).isNotNull();
    }

    @Test
    @DisplayName("JwtAuthenticationConverter can be created")
    void testJwtAuthenticationConverterCanBeCreated() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        assertThat(converter).isNotNull();
    }
}
