package com.company.procurement.platform.api;

import com.company.procurement.platform.api.dto.SubscriptionFeatureRequest;
import com.company.procurement.platform.application.PlatformAuditService;
import com.company.procurement.subscription.application.SubscriptionFeatureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for configurable feature catalogue used when composing SaaS plans. */
@RestController
@RequestMapping("/api/v1/platform/subscription-features")
@Tag(
    name = "Platform subscription features",
    description = "Configurable feature catalogue used when composing SaaS plans")
public class PlatformFeatureController {

  private final SubscriptionFeatureService features;
  private final PlatformAuditService audit;

  public PlatformFeatureController(
      SubscriptionFeatureService features, PlatformAuditService audit) {
    this.features = features;
    this.audit = audit;
  }

  @GetMapping
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public List<SubscriptionFeatureService.FeatureView> list() {
    return features.allFeatures();
  }

  @PostMapping
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public SubscriptionFeatureService.FeatureView create(
      @Valid @RequestBody SubscriptionFeatureRequest request) {
    SubscriptionFeatureService.FeatureView created =
        features.create(request.code(), command(request));
    audit.record("subscription_feature_created", null, Map.of("featureCode", created.code()));
    return created;
  }

  @PutMapping("/{code}")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public SubscriptionFeatureService.FeatureView update(
      @PathVariable String code, @Valid @RequestBody SubscriptionFeatureRequest request) {
    SubscriptionFeatureService.FeatureView updated = features.update(code, command(request));
    audit.record("subscription_feature_updated", null, Map.of("featureCode", updated.code()));
    return updated;
  }

  @DeleteMapping("/{code}")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<Void> deactivate(@PathVariable String code) {
    features.deactivate(code);
    audit.record("subscription_feature_deactivated", null, Map.of("featureCode", code));
    return ResponseEntity.noContent().build();
  }

  private static SubscriptionFeatureService.FeatureCommand command(
      SubscriptionFeatureRequest request) {
    return new SubscriptionFeatureService.FeatureCommand(
        request.name(),
        request.description(),
        request.category(),
        request.active(),
        request.sortOrder());
  }
}
