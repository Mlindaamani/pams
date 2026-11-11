package com.company.procurement.shared.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.mail")
public record MailProperties(Boolean enabled, String from, String appUrl) {

  public MailProperties {
    enabled = enabled != null && enabled;
    from = from == null ? "no-reply@pams.local" : from;
    appUrl = appUrl == null ? "http://localhost:3000" : appUrl;
  }
}
