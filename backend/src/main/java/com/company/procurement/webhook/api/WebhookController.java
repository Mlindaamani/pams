package com.company.procurement.webhook.api;

import com.company.procurement.shared.web.ConditionalRequests;
import com.company.procurement.webhook.api.dto.CreateWebhookRequest;
import com.company.procurement.webhook.api.dto.UpdateWebhookRequest;
import com.company.procurement.webhook.api.dto.WebhookCreatedResponse;
import com.company.procurement.webhook.api.dto.WebhookDeliveryResponse;
import com.company.procurement.webhook.api.dto.WebhookSecretResponse;
import com.company.procurement.webhook.api.dto.WebhookSubscriptionResponse;
import com.company.procurement.webhook.api.mapper.WebhookMapper;
import com.company.procurement.webhook.application.WebhookService;
import com.company.procurement.webhook.domain.WebhookSubscription;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/webhooks")
@PreAuthorize("@permissions.has('webhook:manage')")
@Tag(name = "Webhooks", description = "Tenant webhook subscriptions and signed delivery log")
@com.company.procurement.subscription.application.RequiresFeature("webhooks")
public class WebhookController {

  private final WebhookService webhooks;
  private final WebhookMapper mapper;

  public WebhookController(WebhookService webhooks, WebhookMapper mapper) {
    this.webhooks = webhooks;
    this.mapper = mapper;
  }

  @GetMapping
  public List<WebhookSubscriptionResponse> list() {
    return webhooks.list().stream().map(mapper::toResponse).toList();
  }

  @PostMapping
  public ResponseEntity<WebhookCreatedResponse> create(
      @Valid @RequestBody CreateWebhookRequest request) {
    WebhookService.CreatedSubscription created =
        webhooks.create(
            new WebhookService.CreateWebhookCommand(
                request.url(),
                request.description(),
                request.eventTypes(),
                request.active() == null || request.active()));
    WebhookSubscription subscription = created.subscription();
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/webhooks/{id}")
            .buildAndExpand(subscription.getId())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(subscription.getVersion()))
        .body(new WebhookCreatedResponse(mapper.toResponse(subscription), created.signingSecret()));
  }

  @PutMapping("/{id}")
  public ResponseEntity<WebhookSubscriptionResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWebhookRequest request,
      HttpServletRequest httpRequest) {
    WebhookSubscription updated =
        webhooks.update(
            id,
            ConditionalRequests.requireIfMatch(httpRequest),
            new WebhookService.UpdateWebhookCommand(
                request.url(), request.description(), request.eventTypes(), request.active()));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
    webhooks.delete(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/rotate-secret")
  public ResponseEntity<WebhookSecretResponse> rotateSecret(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    WebhookService.RotatedSecret rotated =
        webhooks.rotateSecret(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(rotated.subscription().getVersion()))
        .body(
            new WebhookSecretResponse(
                rotated.signingSecret(), rotated.subscription().getVersion()));
  }

  @GetMapping("/{id}/deliveries")
  public List<WebhookDeliveryResponse> deliveries(
      @PathVariable UUID id, @RequestParam(defaultValue = "50") int limit) {
    return webhooks.deliveries(id, limit).stream().map(mapper::toResponse).toList();
  }

  @PostMapping("/deliveries/{id}/retry")
  public WebhookDeliveryResponse retryDelivery(@PathVariable UUID id) {
    return mapper.toResponse(webhooks.retryDelivery(id));
  }
}
