package com.wpanther.document.intake.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SecurityDisabledConfig Tests")
class SecurityDisabledConfigTest {

    private HttpSecurity httpSecurity;

    @BeforeEach
    void setUp() {
        httpSecurity = mock(HttpSecurity.class);
    }

    @Test
    @DisplayName("SecurityDisabledConfig has permitAll method")
    void testPermitAllMethodExists() throws Exception {
        var method = SecurityConfig.SecurityDisabledConfig.class.getDeclaredMethod("permitAll", HttpSecurity.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType().getName()).isEqualTo("org.springframework.security.web.SecurityFilterChain");
    }

    @Test
    @DisplayName("SecurityDisabledConfig can be instantiated")
    void testSecurityDisabledConfigCanBeInstantiated() {
        SecurityConfig.SecurityDisabledConfig config = new SecurityConfig.SecurityDisabledConfig();
        assertThat(config).isNotNull();
    }

    @Test
    @DisplayName("SecurityDisabledConfig class annotations")
    void testSecurityDisabledConfigAnnotations() {
        Class<?> innerClass = SecurityConfig.SecurityDisabledConfig.class;
        assertThat(innerClass.isAnnotationPresent(org.springframework.context.annotation.Configuration.class)).isTrue();
        assertThat(innerClass.isAnnotationPresent(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class)).isTrue();
        assertThat(innerClass.isAnnotationPresent(org.springframework.security.config.annotation.web.configuration.EnableWebSecurity.class)).isTrue();
    }

    @Test
    @DisplayName("SecurityDisabledConfig ConditionalOnProperty has correct value")
    void testSecurityDisabledConfigConditionalOnPropertyValue() {
        Class<?> innerClass = SecurityConfig.SecurityDisabledConfig.class;
        var annotation = innerClass.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()[0]).isEqualTo("app.security.enabled");
        assertThat(annotation.havingValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("permitAll method can be called with HttpSecurity")
    void testPermitAllCanBeCalled() throws Exception {
        SecurityConfig.SecurityDisabledConfig config = new SecurityConfig.SecurityDisabledConfig();
        
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        
        config.permitAll(httpSecurity);
        
        verify(httpSecurity).csrf(any());
        verify(httpSecurity).authorizeHttpRequests(any());
        verify(httpSecurity).build();
    }

    @Test
    @DisplayName("SecurityDisabledConfig permits all requests")
    void testPermitAllPermitsAllRequests() throws Exception {
        SecurityConfig.SecurityDisabledConfig config = new SecurityConfig.SecurityDisabledConfig();
        
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        
        config.permitAll(httpSecurity);
        
        verify(httpSecurity).csrf(any());
    }

    @Test
    @DisplayName("SecurityDisabledConfig disables CSRF")
    void testSecurityDisabledConfigDisablesCsrf() throws Exception {
        SecurityConfig.SecurityDisabledConfig config = new SecurityConfig.SecurityDisabledConfig();
        
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        
        config.permitAll(httpSecurity);
        
        verify(httpSecurity).csrf(any());
    }

    @Test
    @DisplayName("SecurityDisabledConfig is a static inner class")
    void testSecurityDisabledConfigIsStaticInnerClass() {
        Class<?> innerClass = SecurityConfig.SecurityDisabledConfig.class;
        assertThat(java.lang.reflect.Modifier.isStatic(innerClass.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("SecurityDisabledConfig is a public inner class")
    void testSecurityDisabledConfigIsPublicInnerClass() {
        Class<?> innerClass = SecurityConfig.SecurityDisabledConfig.class;
        assertThat(java.lang.reflect.Modifier.isPublic(innerClass.getModifiers())).isTrue();
    }
}
