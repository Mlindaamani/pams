package com.company.procurement.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.storage")
public record StorageProperties(
    Boolean enabled, String endpoint, String accessKey, String secretKey, String bucket) {

  public StorageProperties {
    enabled = enabled != null && enabled;
  }
}
