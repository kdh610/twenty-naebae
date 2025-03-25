package com.gaebal_easy.client.hub.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    private static final String REDISSON_HOST_PREFIX = "redis://";

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
//        config.useSingleServer().setAddress(REDISSON_HOST_PREFIX + "redis:6379");
        config.useSingleServer().setAddress(REDISSON_HOST_PREFIX + "localhost:6379").setPassword("systempass");
        return Redisson.create(config);
    }

}
