package com.company.procurement.subscription.api;

import com.company.procurement.subscription.application.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling Stripe webhook events. This controller is responsible for receiving
 * webhook events from Stripe, verifying their signatures, and delegating the processing of these
 * events to the SubscriptionService.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Stripe webhooks", description = "Signature-verified Stripe test-mode events")
public class StripeWebhookController {
  private final SubscriptionService subscriptions;

  public StripeWebhookController(SubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @PostMapping("/stripe")
  public ResponseEntity<Void> stripe(
      @RequestBody String payload,
      @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
    subscriptions.handleWebhook(payload, signature);
    return ResponseEntity.ok().build();
  }
}
