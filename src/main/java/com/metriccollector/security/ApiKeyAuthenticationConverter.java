package com.metriccollector.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts API key from the {@code X-API-Key} request header (or {@code Authorization: Bearer ...})
 * and converts it into an {@link ApiKeyAuthenticationToken}.
 */
public class ApiKeyAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // Try X-API-Key header first
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        // Fallback to Authorization: Bearer <key>
        if (apiKey == null || apiKey.isBlank()) {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            return Mono.empty();
        }

        return Mono.just(new ApiKeyAuthenticationToken(apiKey));
    }
}
