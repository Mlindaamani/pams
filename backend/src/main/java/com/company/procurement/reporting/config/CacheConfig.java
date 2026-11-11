package com.company.procurement.reporting.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

  public static final String SPEND_CACHE = "spendReport";
  public static final String CYCLE_TIME_CACHE = "cycleTimeReport";
  public static final String COMMITMENTS_CACHE = "commitmentsReport";
  public static final String PLATFORM_DASHBOARD_CACHE = "platformDashboard";

  @Bean
  @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
  public RedisCacheManagerBuilderCustomizer reportCacheCustomizer(ReportingProperties properties) {
    RedisCacheConfiguration configuration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(properties.cacheTtl())
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new JdkSerializationRedisSerializer()));
    return builder ->
        builder
            .withCacheConfiguration(SPEND_CACHE, configuration)
            .withCacheConfiguration(CYCLE_TIME_CACHE, configuration)
            .withCacheConfiguration(COMMITMENTS_CACHE, configuration)
            .withCacheConfiguration(PLATFORM_DASHBOARD_CACHE, configuration);
  }
}
