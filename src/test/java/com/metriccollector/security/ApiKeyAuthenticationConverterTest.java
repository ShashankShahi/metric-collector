package com.metriccollector.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import reactor.test.StepVerifier;

class ApiKeyAuthenticationConverterTest {

    private ApiKeyAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ApiKeyAuthenticationConverter();
    }

    @Test
    void testHeaderApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("X-API-Key", "my-key")
                        .build()
        );

        StepVerifier.create(converter.convert(exchange))
                .expectNextMatches(auth -> auth.getCredentials().equals("my-key") && !auth.isAuthenticated())
                .verifyComplete();
    }

    @Test
    void testBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("Authorization", "Bearer my-token")
                        .build()
        );

        StepVerifier.create(converter.convert(exchange))
                .expectNextMatches(auth -> auth.getCredentials().equals("my-token") && !auth.isAuthenticated())
                .verifyComplete();
    }

    @Test
    void testNoToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").build()
        );

        StepVerifier.create(converter.convert(exchange))
                .verifyComplete();
    }
}
