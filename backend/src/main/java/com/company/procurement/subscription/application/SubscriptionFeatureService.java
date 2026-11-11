package com.company.procurement.subscription.application;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionFeatureService {

  private static final Set<String> CATEGORIES =
      Set.of("CORE", "PROCUREMENT", "FINANCE", "INTEGRATIONS", "SECURITY", "SUPPORT");

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public SubscriptionFeatureService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<FeatureView> allFeatures() {
    return jdbc.query(
        "select * from subscription_feature order by sort_order, code", (rs, row) -> view(rs));
  }

  @Transactional(readOnly = true)
  public List<FeatureView> activeFeatures() {
    return jdbc.query(
        "select * from subscription_feature where active = true order by sort_order, code",
        (rs, row) -> view(rs));
  }

  @Transactional(readOnly = true)
  public Map<String, List<FeatureView>> featuresForPlans(List<String> planCodes) {
    if (planCodes == null || planCodes.isEmpty()) return Map.of();
    String placeholders = String.join(",", java.util.Collections.nCopies(planCodes.size(), "?"));
    Map<String, List<FeatureView>> result = new LinkedHashMap<>();
    jdbc.query(
        """
            select pf.plan_code, f.*
              from subscription_plan_feature pf
              join subscription_feature f on f.code = pf.feature_code
             where pf.plan_code in (%s)
             order by f.sort_order, f.code
            """
            .formatted(placeholders),
        rs -> {
          result
              .computeIfAbsent(rs.getString("plan_code"), ignored -> new ArrayList<>())
              .add(view(rs));
        },
        planCodes.toArray());
    return result;
  }

  @Transactional
  public FeatureView create(String codeValue, FeatureCommand command) {
    String code = normalizeCode(codeValue);
    if (!jdbc.queryForList("select 1 from subscription_feature where code = ?", code).isEmpty()) {
      throw new ConflictException("Subscription feature %s already exists".formatted(code));
    }
    validate(command);
    java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
    jdbc.update(
        """
            insert into subscription_feature
                (code, name, description, category, active, sort_order,
                 created_at, created_by, updated_at, updated_by)
            values (?, ?, ?, ?, ?, ?, ?, 'platform-admin', ?, 'platform-admin')
            """,
        code,
        command.name().trim(),
        command.description().trim(),
        command.category().toUpperCase(Locale.ROOT),
        command.active(),
        command.sortOrder(),
        now,
        now);
    return feature(code);
  }

  @Transactional
  public FeatureView update(String codeValue, FeatureCommand command) {
    String code = normalizeExistingCode(codeValue);
    validate(command);
    jdbc.update(
        """
            update subscription_feature
               set name = ?, description = ?, category = ?, active = ?, sort_order = ?,
                   updated_at = ?, updated_by = 'platform-admin'
             where code = ?
            """,
        command.name().trim(),
        command.description().trim(),
        command.category().toUpperCase(Locale.ROOT),
        command.active(),
        command.sortOrder(),
        java.sql.Timestamp.from(clock.instant()),
        code);
    return feature(code);
  }

  @Transactional
  public void deactivate(String codeValue) {
    String code = normalizeExistingCode(codeValue);
    jdbc.update(
        "update subscription_feature set active = false, updated_at = ?, updated_by = 'platform-admin' where code = ?",
        java.sql.Timestamp.from(clock.instant()),
        code);
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
    List<FeatureView> features = featuresByCodes(new ArrayList<>(normalized));
    if (features.size() != normalized.size()) {
      Set<String> found = new LinkedHashSet<>(features.stream().map(FeatureView::code).toList());
      normalized.removeAll(found);
      throw new BusinessRuleException(
          "Unknown subscription feature(s): " + String.join(", ", normalized));
    }
    for (FeatureView feature : features) {
      if (!feature.active() && !currentlyAssigned.contains(feature.code())) {
        throw new BusinessRuleException(
            "Subscription feature %s is inactive and cannot be newly assigned"
                .formatted(feature.code()));
      }
    }
    return features;
  }

  private List<FeatureView> featuresByCodes(List<String> codes) {
    if (codes.isEmpty()) return List.of();
    String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
    return jdbc.query(
        "select * from subscription_feature where code in (%s) order by sort_order, code"
            .formatted(placeholders),
        (rs, row) -> view(rs),
        codes.toArray());
  }

  private FeatureView feature(String code) {
    return jdbc
        .query("select * from subscription_feature where code = ?", (rs, row) -> view(rs), code)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException("Subscription feature %s not found".formatted(code)));
  }

  private static FeatureView view(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new FeatureView(
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getBoolean("active"),
        rs.getInt("sort_order"));
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

  private String normalizeExistingCode(String codeValue) {
    String code = normalizeCode(codeValue);
    if (jdbc.queryForList("select 1 from subscription_feature where code = ?", code).isEmpty()) {
      throw new ResourceNotFoundException("Subscription feature %s not found".formatted(code));
    }
    return code;
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
