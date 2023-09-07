package com.mycompany.gatewayservices.filter;

import com.mycompany.gatewayservices.configuration.JwtTokenService;
import com.mycompany.gatewayservices.route.RouterValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@RefreshScope
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GatewayFilter {
    private final JwtTokenService jwtTokenService;
    private final RouterValidator routerValidator;
    @Value("${applications.jwtConfig.prefix}")
    private String prefix;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        if (routerValidator.isSecured.test(request)) {

            if (this.isAuthMissing(request)) {
                log.error("Authorization  header is missing.");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is missing in request.");
            }

            Optional<String> token = resolveHeaderToken(request);

            if (!token.isPresent() || !jwtTokenService.validateToken(token.get()) || jwtTokenService.isTokenExpired(token.get())) {
                log.error("Authorization header is invalid.");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is invalid.");

            }
            this.populateRequestWithHeaders(exchange, token.get());
        }
        return chain.filter(exchange);
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION);
    }

    private String getAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
    }

    private Optional<String> resolveHeaderToken(ServerHttpRequest request) {
        final String authorizationHeader = getAuthHeader(request);
        return authorizationHeader != null && authorizationHeader.startsWith(prefix + " ") ?
                Optional.of(authorizationHeader.substring((prefix + " ").length())) : Optional.empty();
    }
    private void populateRequestWithHeaders(ServerWebExchange exchange, String token) {
        Claims claims = jwtTokenService.extractAllClaimsFromToken(token);
        exchange.getRequest().mutate()
                .header("username", claims.getSubject())
                .header("authorities", String.valueOf(claims.get("authorities")))
                .build();
    }
}
