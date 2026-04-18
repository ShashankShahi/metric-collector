package com.metriccollector.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Authentication token representing a validated API key.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;

    /**
     * Creates an unauthenticated token (before validation).
     */
    public ApiKeyAuthenticationToken(String apiKey) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.apiKey = apiKey;
        setAuthenticated(false);
    }

    /**
     * Creates an authenticated token (after validation).
     */
    public static ApiKeyAuthenticationToken authenticated(String apiKey) {
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(apiKey);
        token.setAuthenticated(true);
        return token;
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return "api-key-user";
    }
}
