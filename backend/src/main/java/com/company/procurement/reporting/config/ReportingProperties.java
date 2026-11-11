package com.company.procurement.reporting.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.reporting")
public record ReportingProperties(Duration cacheTtl) {

  public ReportingProperties {
    cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
  }
}
