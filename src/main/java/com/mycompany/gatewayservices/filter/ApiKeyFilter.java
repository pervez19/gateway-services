package com.mycompany.gatewayservices.filter;

import com.mycompany.gatewayservices.configuration.RedisHashComponent;
import com.mycompany.gatewayservices.constant.AppConstant;
import com.mycompany.gatewayservices.domain.ApiKey;
import com.mycompany.gatewayservices.mapper.ObjectMapperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFilter implements GlobalFilter, Ordered {
    private final RedisHashComponent redisHashComponent;
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        List<String> apiKeyHeader = exchange.getRequest().getHeaders().get(AppConstant.GATEWAY_KEY);
        log.info("Api key: {}", apiKeyHeader);

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);

        String routeId = route != null ? route.getId() : null;
        if (routeId == null || CollectionUtils.isEmpty(apiKeyHeader) || !isAuthorized(routeId, apiKeyHeader.get(0))) {
            log.error("Api Key not valid");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You cannot consume this service. Please check your api key.");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean isAuthorized(String routeId, String apikey) {

        Object apiKeyObject = redisHashComponent.hashGet(AppConstant.RECORD_KEY, apikey);

        if(apiKeyObject != null){
            ApiKey key = ObjectMapperUtils.objectMapper(apiKeyObject, ApiKey.class);
            return  key.getServices().contains(routeId);
        }else {
            return false;
        }
    }
}
