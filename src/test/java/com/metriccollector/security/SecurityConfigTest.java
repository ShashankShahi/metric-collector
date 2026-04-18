package com.metriccollector.security;

import com.metriccollector.config.MetricCollectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig config;
    private MetricCollectorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MetricCollectorProperties();
        properties.getSecurity().setRsocketToken("rsocket-token");
        config = new SecurityConfig(properties);
    }

    @Test
    void testRsocketAuthenticationManagerValidToken() {
        ReactiveAuthenticationManager manager = config.rsocketAuthenticationManager();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", "rsocket-token");

        StepVerifier.create(manager.authenticate(auth))
                .expectNextMatches(result -> result.isAuthenticated() && result.getCredentials().equals("rsocket-token"))
                .verifyComplete();
    }

    @Test
    void testRsocketAuthenticationManagerInvalidToken() {
        ReactiveAuthenticationManager manager = config.rsocketAuthenticationManager();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", "invalid");

        StepVerifier.create(manager.authenticate(auth))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void testMessageHandler() {
        RSocketStrategies strategies = RSocketStrategies.builder().build();
        RSocketMessageHandler handler = config.messageHandler(strategies);
        assertNotNull(handler);
    }

    @Test
    void testSecurityWebFilterChain() {
        org.springframework.security.config.web.server.ServerHttpSecurity http = 
            org.springframework.security.config.web.server.ServerHttpSecurity.http();
        org.springframework.security.web.server.SecurityWebFilterChain chain = 
            config.securityWebFilterChain(http);
        assertNotNull(chain);
    }
}
