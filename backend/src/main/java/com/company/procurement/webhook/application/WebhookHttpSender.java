package com.company.procurement.webhook.application;

import com.company.procurement.shared.config.WebhookProperties;
import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookSubscription;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WebhookHttpSender {

  private final WebhookProperties properties;
  private final WebhookSigner signer;
  private final HttpClient client;

  public WebhookHttpSender(WebhookProperties properties, WebhookSigner signer) {
    this.properties = properties;
    this.signer = signer;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public Attempt send(WebhookSubscription subscription, WebhookDelivery delivery) {
    long timestamp = System.currentTimeMillis() / 1000;
    String signature = signer.sign(subscription.getSecret(), timestamp, delivery.getPayload());
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(subscription.getUrl()))
            .timeout(Duration.ofSeconds(subscription.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header("User-Agent", "PAMS-Webhooks/1.0")
            .header("X-PAMS-Event", delivery.getEventType())
            .header("X-PAMS-Delivery", delivery.getId().toString())
            .header("X-PAMS-Timestamp", Long.toString(timestamp))
            .header("X-PAMS-Signature", signature)
            .header("X-PAMS-Attempt", Integer.toString(delivery.getAttempts() + 1))
            .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
            .build();
    long started = System.nanoTime();
    try {
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int duration = (int) ((System.nanoTime() - started) / 1_000_000);
      boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
      return new Attempt(
          success,
          response.statusCode(),
          response.body(),
          success ? null : "HTTP " + response.statusCode(),
          duration);
    } catch (Exception ex) {
      int duration = (int) ((System.nanoTime() - started) / 1_000_000);
      return new Attempt(
          false, 0, null, ex.getClass().getSimpleName() + ": " + ex.getMessage(), duration);
    }
  }

  public record Attempt(boolean success, int status, String body, String error, int durationMs) {}
}
