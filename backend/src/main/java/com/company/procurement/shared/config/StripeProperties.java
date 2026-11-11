package com.company.procurement.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.stripe")
public record StripeProperties(
    String secretKey,
    String webhookSecret,
    String successUrl,
    String cancelUrl,
    String portalReturnUrl) {

  public boolean configured() {
    return secretKey != null && !secretKey.isBlank();
  }
}
