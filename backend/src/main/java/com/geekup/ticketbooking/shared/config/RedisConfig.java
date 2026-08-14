package com.geekup.ticketbooking.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration that registers the additional {@link RedisTemplate} beans
 * required by the application.
 *
 * <p>Spring Boot auto-configures a {@code RedisTemplate<Object,Object>} and a
 * {@code StringRedisTemplate}. We register a specialised
 * {@code RedisTemplate<String,Long>} for inventory counters so that values are
 * stored and retrieved as plain Long integers without Java serialization overhead.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * {@link RedisTemplate} that stores keys as UTF-8 strings and values as
     * Long integers (serialised via their {@code toString()} representation).
     *
     * <p>Used by {@link com.geekup.ticketbooking.shared.cache.InventoryCache}.</p>
     */
    @Bean("longRedisTemplate")
    public RedisTemplate<String, Long> longRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.afterPropertiesSet();
        return template;
    }
}
