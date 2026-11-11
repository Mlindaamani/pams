package com.company.procurement.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.rate-limit")
public record RateLimitProperties(
    Boolean enabled, int capacity, int refillTokens, int refillPeriodSeconds) {

  public RateLimitProperties {
    enabled = enabled == null || enabled;
    capacity = capacity <= 0 ? 120 : capacity;
    refillTokens = refillTokens <= 0 ? capacity : refillTokens;
    refillPeriodSeconds = refillPeriodSeconds <= 0 ? 60 : refillPeriodSeconds;
  }
}
