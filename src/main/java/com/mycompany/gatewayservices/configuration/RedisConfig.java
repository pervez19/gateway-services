package com.mycompany.gatewayservices.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

@Configuration
public class RedisConfig {
    private final Environment environment;

    public RedisConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(Objects.requireNonNull(environment.getProperty("spring.data.redis.host")), Integer.parseInt(Objects.requireNonNull(environment.getProperty("spring.data.redis.port"))));
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        template.setEnableTransactionSupport(true);

        return template;
    }
}
