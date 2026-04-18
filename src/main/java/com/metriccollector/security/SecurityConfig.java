package com.metriccollector.security;

import com.metriccollector.config.MetricCollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import reactor.core.publisher.Mono;

/**
 * Security configuration for both REST (WebFlux) and RSocket endpoints.
 * <p>
 * <b>REST</b>: API key in {@code X-API-Key} header or {@code Authorization: Bearer <key>}.
 * <br>
 * <b>RSocket</b>: Token-based SIMPLE authentication via SETUP frame metadata.
 */
@Configuration
@EnableWebFluxSecurity
@EnableRSocketSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final MetricCollectorProperties properties;

    public SecurityConfig(MetricCollectorProperties properties) {
        this.properties = properties;
    }

    // ==================== REST (WebFlux) Security ====================

    /**
     * Configures the WebFlux security filter chain.
     * <ul>
     *   <li>Health endpoint is open</li>
     *   <li>All {@code /api/**} endpoints require API key authentication</li>
     *   <li>Actuator endpoints are permitted (optional — can be locked down)</li>
     * </ul>
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ReactiveAuthenticationManager authManager = new ApiKeyAuthenticationManager(properties);

        AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(authManager);
        apiKeyFilter.setServerAuthenticationConverter(new ApiKeyAuthenticationConverter());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Health endpoint is open
                        .pathMatchers("/api/v1/metrics/health").permitAll()
                        // Actuator is open (for ops tooling)
                        .pathMatchers("/actuator/**").permitAll()
                        // All other API endpoints require authentication
                        .pathMatchers("/api/**").authenticated()
                        // Everything else is denied
                        .anyExchange().denyAll()
                )
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    // ==================== RSocket Security ====================

    /**
     * Configures RSocket security with SIMPLE authentication.
     * Clients must provide the configured token in the SETUP frame.
     */
    @Bean
    public PayloadSocketAcceptorInterceptor rsocketInterceptor(RSocketSecurity rsocketSecurity) {
        rsocketSecurity
                .authorizePayload(authorize -> authorize
                        // All routes require authentication
                        .anyRequest().authenticated()
                        .anyExchange().permitAll()
                )
                .simpleAuthentication(simple ->
                        simple.authenticationManager(rsocketAuthenticationManager())
                );

        return rsocketSecurity.build();
    }

    /**
     * Authentication manager for RSocket SIMPLE auth.
     * Validates the provided username/password against the configured token.
     */
    @Bean
    public ReactiveAuthenticationManager rsocketAuthenticationManager() {
        return authentication -> {
            String credentials = authentication.getCredentials().toString();
            String expectedToken = properties.getSecurity().getRsocketToken();

            if (expectedToken.equals(credentials)) {
                log.debug("RSocket authentication successful");
                return Mono.just(ApiKeyAuthenticationToken.authenticated(credentials));
            }

            log.warn("RSocket authentication failed — invalid token");
            return Mono.error(new BadCredentialsException("Invalid RSocket token"));
        };
    }

    /**
     * Configures RSocketMessageHandler with security argument resolvers.
     */
    @Bean
    public RSocketMessageHandler messageHandler(RSocketStrategies strategies) {
        RSocketMessageHandler handler = new RSocketMessageHandler();
        handler.getArgumentResolverConfigurer().addCustomResolver(
                new AuthenticationPrincipalArgumentResolver());
        handler.setRSocketStrategies(strategies);
        return handler;
    }
}
