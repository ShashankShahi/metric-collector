package com.metriccollector.security;

import com.metriccollector.config.MetricCollectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import reactor.test.StepVerifier;

class ApiKeyAuthenticationManagerTest {

    private ApiKeyAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getSecurity().setApiKey("valid-key");
        manager = new ApiKeyAuthenticationManager(props);
    }

    @Test
    void testValidKey() {
        ApiKeyAuthenticationToken unauthenticated = new ApiKeyAuthenticationToken("valid-key");

        StepVerifier.create(manager.authenticate(unauthenticated))
                .expectNextMatches(Authentication::isAuthenticated)
                .verifyComplete();
    }

    @Test
    void testInvalidKey() {
        ApiKeyAuthenticationToken unauthenticated = new ApiKeyAuthenticationToken("invalid-key");

        StepVerifier.create(manager.authenticate(unauthenticated))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void testEmptyKey() {
        ApiKeyAuthenticationToken unauthenticated = new ApiKeyAuthenticationToken("");

        StepVerifier.create(manager.authenticate(unauthenticated))
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
