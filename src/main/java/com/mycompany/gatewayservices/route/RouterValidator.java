package com.mycompany.gatewayservices.route;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouterValidator {


    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/auth/signin",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh-token" // access token may be expired; the refresh token is checked by auth service
    );

    // Exact match: 'contains' let /api/v1/users/api/v1/auth/signin skip JWT validation.
    public Predicate<ServerHttpRequest> isSecured =
            request -> !openApiEndpoints.contains(request.getURI().getPath());

}
