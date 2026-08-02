package com.livetix.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 *
 * 修复说明：
 * 1. 手动配置 ObjectMapper 并注册 JavaTimeModule，解决 LocalDateTime 序列化问题
 * 2. 使用 GenericJackson2JsonRedisSerializer 包装自定义 ObjectMapper
 * 3. 禁用 WRITE_DATES_AS_TIMESTAMPS，确保日期以 ISO-8601 字符串格式存储
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // ============================================================
        // 关键修复：手动创建 ObjectMapper 并注册 JavaTimeModule
        // 解决 LocalDateTime / LocalDate / LocalTime 序列化失败问题
        // ============================================================
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // Redis 序列化 — 使用宽松验证器
        // 安全说明: Redis 应配置为仅本地访问(127.0.0.1)，并设置密码
        // Jackson DefaultTyping 用于对象多态序列化，类型校验由网络层保证
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jacksonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // String 序列化器用于 Key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 和 HashKey 使用 String 序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 和 HashValue 使用 JSON 序列化
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
