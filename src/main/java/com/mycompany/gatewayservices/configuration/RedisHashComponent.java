package com.mycompany.gatewayservices.configuration;

import com.mycompany.gatewayservices.mapper.ObjectMapperUtils;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHashComponent {
    private final RedisTemplate<String, Object> redisTemplate;

    public void hashSet(String key, Object hashKey, @NotNull Object val) {
        Map ruleHash = ObjectMapperUtils.objectMapper(val, Map.class);
        redisTemplate.opsForHash().put(key, hashKey, ruleHash);
    }

    public List<Object> hashValues(String key) {
        return redisTemplate.opsForHash().values(key);
    }

    public Object hashGet(String key, Object hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }
}
