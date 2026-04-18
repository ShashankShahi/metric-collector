package com.metriccollector.security;

import com.metriccollector.config.MetricCollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * Validates API key credentials against the configured key.
 */
public class ApiKeyAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationManager.class);

    private final String validApiKey;

    public ApiKeyAuthenticationManager(MetricCollectorProperties properties) {
        this.validApiKey = properties.getSecurity().getApiKey();
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String providedKey = (String) authentication.getCredentials();

        if (validApiKey.equals(providedKey)) {
            log.debug("API key authentication successful");
            return Mono.just(ApiKeyAuthenticationToken.authenticated(providedKey));
        }

        log.warn("API key authentication failed — invalid key provided");
        return Mono.error(new BadCredentialsException("Invalid API key"));
    }
}
