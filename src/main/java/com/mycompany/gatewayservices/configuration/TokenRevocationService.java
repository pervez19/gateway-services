package com.mycompany.gatewayservices.configuration;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Reads the JWT deny-list written by authentication-services (same Redis, same key contract):
 * {@code jwt:revoked:<jti>} for a logged-out token, {@code jwt:revoked-before:<username>} (epoch seconds)
 * to revoke every token of a user issued at or before that moment.
 */
@Component
@RequiredArgsConstructor
public class TokenRevocationService {
    private static final String REVOKED_TOKEN_KEY = "jwt:revoked:";
    private static final String REVOKED_BEFORE_KEY = "jwt:revoked-before:";

    private final StringRedisTemplate redis;

    public Mono<Boolean> isRevoked(Claims claims) {
        // StringRedisTemplate (Jedis) is blocking: keep it off the Netty event loop.
        return Mono.fromCallable(() -> {
                    List<String> values = redis.opsForValue().multiGet(
                            List.of(REVOKED_TOKEN_KEY + claims.getId(), REVOKED_BEFORE_KEY + claims.getSubject()));
                    if (values == null) {
                        return false;
                    }
                    String revokedBefore = values.get(1);
                    return values.get(0) != null
                            || (revokedBefore != null && claims.getIssuedAt() != null
                            && claims.getIssuedAt().toInstant().getEpochSecond() <= Long.parseLong(revokedBefore));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
