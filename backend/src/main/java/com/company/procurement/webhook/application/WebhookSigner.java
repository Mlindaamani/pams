package com.company.procurement.webhook.application;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookSigner {

  public String sign(String secret, long timestamp, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
      return "sha256=" + HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not sign webhook payload", ex);
    }
  }
}
