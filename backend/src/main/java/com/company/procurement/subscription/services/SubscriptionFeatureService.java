package com.company.procurement.subscription.services;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.subscription.domain.SubscriptionFeature;
import com.company.procurement.subscription.repositories.SubscriptionFeatureRepository;
import com.company.procurement.subscription.repositories.SubscriptionPlanFeatureRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads plan features and tenant entitlements. */
@Service
public class SubscriptionFeatureService {

  private static final Set<String> CATEGORIES =
      Set.of("CORE", "PROCUREMENT", "FINANCE", "INTEGRATIONS", "SECURITY", "SUPPORT");

  private final SubscriptionFeatureRepository features;
  private final SubscriptionPlanFeatureRepository planFeatures;

  public SubscriptionFeatureService(
      SubscriptionFeatureRepository features, SubscriptionPlanFeatureRepository planFeatures) {
    this.features = features;
    this.planFeatures = planFeatures;
  }

  @Transactional(readOnly = true)
  public List<FeatureView> allFeatures() {
    return features.findAllOrdered().stream().map(SubscriptionFeatureService::view).toList();
  }

  @Transactional(readOnly = true)
  public List<FeatureView> activeFeatures() {
    return features.findActiveOrdered().stream().map(SubscriptionFeatureService::view).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, List<FeatureView>> featuresForPlans(List<String> planCodes) {
    if (planCodes == null || planCodes.isEmpty()) return Map.of();
    Map<String, List<FeatureView>> result = new LinkedHashMap<>();
    for (SubscriptionPlanFeatureRepository.PlanFeatureRow row :
        planFeatures.findFeaturesForPlans(planCodes)) {
      result
          .computeIfAbsent(row.planCode(), ignored -> new ArrayList<>())
          .add(
              new FeatureView(
                  row.code(),
                  row.name(),
                  row.description(),
                  row.category(),
                  row.active(),
                  row.sortOrder()));
    }
    return result;
  }

  @Transactional
  public FeatureView create(String codeValue, FeatureCommand command) {
    String code = normalizeCode(codeValue);
    if (features.existsById(code)) {
      throw new ConflictException("Subscription feature %s already exists".formatted(code));
    }
    validate(command);
    SubscriptionFeature feature =
        features.save(
            new SubscriptionFeature(
                code,
                command.name().trim(),
                command.description().trim(),
                command.category().toUpperCase(Locale.ROOT),
                command.active(),
                command.sortOrder()));
    return view(feature);
  }

  @Transactional
  public FeatureView update(String codeValue, FeatureCommand command) {
    SubscriptionFeature feature = requireFeature(codeValue);
    validate(command);
    feature.update(
        command.name().trim(),
        command.description().trim(),
        command.category().toUpperCase(Locale.ROOT),
        command.active(),
        command.sortOrder());
    return view(features.save(feature));
  }

  @Transactional
  public void deactivate(String codeValue) {
    SubscriptionFeature feature = requireFeature(codeValue);
    feature.deactivate();
    features.save(feature);
  }

  @Transactional(readOnly = true)
  public List<FeatureView> resolveForAssignment(
      List<String> requestedCodes, Set<String> currentlyAssigned) {
    Set<String> normalized = new LinkedHashSet<>();
    if (requestedCodes != null) {
      for (String code : requestedCodes) {
        if (code != null && !code.isBlank()) {
          normalized.add(code.trim().toLowerCase(Locale.ROOT));
        }
      }
    }
    if (normalized.isEmpty()) return List.of();
    List<FeatureView> resolved =
        features.findByCodeIn(normalized).stream().map(SubscriptionFeatureService::view).toList();
    if (resolved.size() != normalized.size()) {
      Set<String> found = new LinkedHashSet<>(resolved.stream().map(FeatureView::code).toList());
      normalized.removeAll(found);
      throw new BusinessRuleException(
          "Unknown subscription feature(s): " + String.join(", ", normalized));
    }
    for (FeatureView feature : resolved) {
      if (!feature.active() && !currentlyAssigned.contains(feature.code())) {
        throw new BusinessRuleException(
            "Subscription feature %s is inactive and cannot be newly assigned"
                .formatted(feature.code()));
      }
    }
    return resolved;
  }

  private SubscriptionFeature requireFeature(String codeValue) {
    String code = normalizeCode(codeValue);
    return features
        .findById(code)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("Subscription feature %s not found".formatted(code)));
  }

  private static FeatureView view(SubscriptionFeature feature) {
    return new FeatureView(
        feature.getCode(),
        feature.getName(),
        feature.getDescription(),
        feature.getCategory(),
        feature.isActive(),
        feature.getSortOrder());
  }

  private static void validate(FeatureCommand command) {
    if (command.name() == null || command.name().isBlank()) {
      throw new BusinessRuleException("Feature name is required");
    }
    if (command.description() == null || command.description().isBlank()) {
      throw new BusinessRuleException("Feature description is required");
    }
    if (command.category() == null
        || !CATEGORIES.contains(command.category().toUpperCase(Locale.ROOT))) {
      throw new BusinessRuleException("Feature category is invalid");
    }
    if (command.sortOrder() < 0) {
      throw new BusinessRuleException("Feature sort order must be zero or positive");
    }
  }

  private static String normalizeCode(String codeValue) {
    String code = codeValue == null ? "" : codeValue.trim().toLowerCase(Locale.ROOT);
    if (!code.matches("[a-z0-9_]{2,50}")) {
      throw new BusinessRuleException(
          "Feature code must be 2-50 lowercase letters, numbers or underscores");
    }
    return code;
  }

  public record FeatureView(
      String code,
      String name,
      String description,
      String category,
      boolean active,
      int sortOrder) {}

  public record FeatureCommand(
      String name, String description, String category, boolean active, int sortOrder) {}
}
