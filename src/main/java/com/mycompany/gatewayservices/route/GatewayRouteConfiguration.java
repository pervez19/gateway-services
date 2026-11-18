package com.mycompany.gatewayservices.route;

import com.mycompany.gatewayservices.constant.AppConstant;
import com.mycompany.gatewayservices.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class GatewayRouteConfiguration {

    private final AuthenticationFilter filter;

    // Route id = service key checked by ApiKeyFilter; lb://<eureka service id> is resolved and round-robined by Spring Cloud LoadBalancer.
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(AppConstant.AUTHENTICATION_SERVICE_KEY,
                        r -> r.path("/api/v1/roles/**", "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/companies/**", "/api/v1/permissions/**")
                                .filters(f -> f.filter(filter).retry(2))
                                .uri("lb://authentication-services"))
                .route(AppConstant.PRODUCT_SERVICE_KEY,
                        r -> r.path("/api/v1/products/**")
                                .filters(f -> f.filter(filter).retry(2))
                                .uri("lb://product-services"))
                .build();
    }
}
