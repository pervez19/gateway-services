package com.mycompany.gatewayservices.route;

import com.mycompany.gatewayservices.constant.AppConstant;
import com.mycompany.gatewayservices.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;


@Configuration
@EnableHystrix
@RequiredArgsConstructor
public class GatewayRouteConfiguration {

    private final AuthenticationFilter filter;
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(AppConstant.AUTHENTICATION_SERVICE_KEY,
                        r -> r.path("/api/v1/roles/**")
                                .filters(f -> f.filter(filter)).uri("http://localhost:8081"))
                .route(AppConstant.AUTHENTICATION_SERVICE_KEY,
                        r -> r.path("/api/v1/auth/**")
                                .filters(f -> f.filter(filter)).uri("http://localhost:8081"))
                .route(AppConstant.AUTHENTICATION_SERVICE_KEY,
                        r -> r.path("/api/v1/users/**")
                                .filters(f -> f.filter(filter)).uri("http://localhost:8081"))
                .route(AppConstant.AUTHENTICATION_SERVICE_KEY,
                        r -> r.path("/api/v1/companies/**")
                                .filters(f -> f.filter(filter)).uri("http://localhost:8081"))
                .route(AppConstant.PRODUCT_SERVICE_KEY,
                        r -> r.path("/api/v1/products/**")
                                .filters(f -> f.filter(filter)).uri("http://localhost:8091"))
                .build();
    }
}
