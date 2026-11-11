package com.company.procurement.organization.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.storage.FileStoragePort;
import com.company.procurement.shared.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBrandingService {

  private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
  private static final Set<String> LOGO_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
  private static final long MAX_FAVICON_BYTES = 512L * 1024;
  private static final Set<String> FAVICON_TYPES =
      Set.of("image/png", "image/svg+xml", "image/x-icon", "image/vnd.microsoft.icon");

  private final JdbcTemplate jdbc;
  private final FileStoragePort storage;
  private final CurrentUser currentUser;
  private final AuditEventService audit;
  private final Clock clock;

  public TenantBrandingService(
      JdbcTemplate jdbc,
      FileStoragePort storage,
      CurrentUser currentUser,
      AuditEventService audit,
      Clock clock) {
    this.jdbc = jdbc;
    this.storage = storage;
    this.currentUser = currentUser;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public BrandingView current() {
    UUID organizationId = TenantContext.require();
    String tenantName =
        jdbc.queryForObject(
            "select name from organization where id = ?", String.class, organizationId);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select * from tenant_branding where organization_id = ?", organizationId);
    if (rows.isEmpty()) {
      return new BrandingView(
          organizationId,
          tenantName,
          "#0B5CAD",
          "#00695C",
          tenantName,
          null,
          false,
          false,
          10,
          "INTER",
          "COMFORTABLE",
          "Requests",
          "Purchase orders",
          "Vendors",
          "Budgets",
          "Catalog items",
          0,
          null);
    }
    Map<String, Object> row = rows.get(0);
    return new BrandingView(
        organizationId,
        (String) row.getOrDefault("display_name", tenantName),
        (String) row.get("primary_color"),
        (String) row.get("secondary_color"),
        (String) row.get("email_from_name"),
        (String) row.get("support_email"),
        row.get("logo_object_key") != null,
        row.get("favicon_object_key") != null,
        ((Number) row.get("corner_radius")).intValue(),
        (String) row.get("font_family"),
        (String) row.get("ui_density"),
        (String) row.get("request_label"),
        (String) row.get("order_label"),
        (String) row.get("vendor_label"),
        (String) row.get("budget_label"),
        (String) row.get("catalog_label"),
        ((Number) row.get("version")).longValue(),
        instant(row.get("updated_at")));
  }

  @Transactional
  public BrandingView update(BrandingCommand command) {
    UUID organizationId = TenantContext.require();
    validate(command);
    Timestamp now = Timestamp.from(clock.instant());
    ensureRow(organizationId, now);
    jdbc.update(
        """
        update tenant_branding
           set display_name = ?, primary_color = ?, secondary_color = ?, email_from_name = ?,
               support_email = ?, corner_radius = ?, font_family = ?, ui_density = ?,
               request_label = ?, order_label = ?, vendor_label = ?, budget_label = ?,
               catalog_label = ?, updated_at = ?, updated_by = ?, version = version + 1
         where organization_id = ?
        """,
        command.displayName().trim(),
        command.primaryColor().toUpperCase(),
        command.secondaryColor().toUpperCase(),
        blankToNull(command.emailFromName()),
        blankToNull(command.supportEmail()),
        command.cornerRadius() == null ? 10 : command.cornerRadius(),
        command.fontFamily() == null ? "INTER" : command.fontFamily().toUpperCase(),
        command.uiDensity() == null ? "COMFORTABLE" : command.uiDensity().toUpperCase(),
        label(command.requestLabel(), "Requests"),
        label(command.orderLabel(), "Purchase orders"),
        label(command.vendorLabel(), "Vendors"),
        label(command.budgetLabel(), "Budgets"),
        label(command.catalogLabel(), "Catalog items"),
        now,
        actor(),
        organizationId);
    audit.recordCurrentActor(
        "TenantBranding",
        organizationId,
        "BRANDING_UPDATED",
        Map.of(
            "displayName", command.displayName().trim(),
            "primaryColor", command.primaryColor().toUpperCase(),
            "secondaryColor", command.secondaryColor().toUpperCase(),
            "fontFamily",
                command.fontFamily() == null ? "INTER" : command.fontFamily().toUpperCase(),
            "uiDensity",
                command.uiDensity() == null ? "COMFORTABLE" : command.uiDensity().toUpperCase()));
    return current();
  }

  @Transactional
  public BrandingView uploadLogo(byte[] content, String contentType) {
    if (content == null || content.length == 0) {
      throw new BusinessRuleException("Logo file is required");
    }
    if (content.length > MAX_LOGO_BYTES) {
      throw new BusinessRuleException("Logo must be 2MB or smaller");
    }
    String type = contentType == null ? "" : contentType.toLowerCase();
    if (!LOGO_TYPES.contains(type)) {
      throw new BusinessRuleException("Logo must be PNG, JPEG, WebP or SVG");
    }
    UUID organizationId = TenantContext.require();
    Timestamp now = Timestamp.from(clock.instant());
    ensureRow(organizationId, now);
    String previous = currentLogoKey(organizationId);
    String extension =
        switch (type) {
          case "image/png" -> "png";
          case "image/webp" -> "webp";
          case "image/svg+xml" -> "svg";
          default -> "jpg";
        };
    String key = "branding/%s/logo-%s.%s".formatted(organizationId, UUID.randomUUID(), extension);
    storage.put(key, content, type);
    jdbc.update(
        """
        update tenant_branding
           set logo_object_key = ?, logo_content_type = ?, updated_at = ?, updated_by = ?,
               version = version + 1
         where organization_id = ?
        """,
        key,
        type,
        now,
        actor(),
        organizationId);
    if (previous != null) {
      try {
        storage.delete(previous);
      } catch (RuntimeException ignored) {
        // New logo is stored; cleanup can be retried manually.
      }
    }
    audit.recordCurrentActor("TenantBranding", organizationId, "BRANDING_LOGO_UPDATED", Map.of());
    return current();
  }

  @Transactional
  public BrandingView uploadFavicon(byte[] content, String contentType) {
    if (content == null || content.length == 0) {
      throw new BusinessRuleException("Favicon file is required");
    }
    if (content.length > MAX_FAVICON_BYTES) {
      throw new BusinessRuleException("Favicon must be 512KB or smaller");
    }
    String type = contentType == null ? "" : contentType.toLowerCase();
    if (!FAVICON_TYPES.contains(type)) {
      throw new BusinessRuleException("Favicon must be PNG, ICO or SVG");
    }
    UUID organizationId = TenantContext.require();
    Timestamp now = Timestamp.from(clock.instant());
    ensureRow(organizationId, now);
    String previous = currentFaviconKey(organizationId);
    String extension =
        switch (type) {
          case "image/png" -> "png";
          case "image/svg+xml" -> "svg";
          default -> "ico";
        };
    String key =
        "branding/%s/favicon-%s.%s".formatted(organizationId, UUID.randomUUID(), extension);
    storage.put(key, content, type);
    jdbc.update(
        """
        update tenant_branding
           set favicon_object_key = ?, favicon_content_type = ?, updated_at = ?,
               updated_by = ?, version = version + 1
         where organization_id = ?
        """,
        key,
        type,
        now,
        actor(),
        organizationId);
    if (previous != null) {
      try {
        storage.delete(previous);
      } catch (RuntimeException ignored) {
        // New favicon is stored; cleanup can be retried manually.
      }
    }
    audit.recordCurrentActor(
        "TenantBranding", organizationId, "BRANDING_FAVICON_UPDATED", Map.of());
    return current();
  }

  @Transactional(readOnly = true)
  public Logo currentFavicon() {
    UUID organizationId = TenantContext.require();
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select favicon_object_key, favicon_content_type from tenant_branding where organization_id = ?",
            organizationId);
    if (rows.isEmpty() || rows.get(0).get("favicon_object_key") == null) {
      throw new ResourceNotFoundException("No branding favicon is configured");
    }
    String key = (String) rows.get(0).get("favicon_object_key");
    String type = (String) rows.get(0).get("favicon_content_type");
    try (var input = storage.get(key)) {
      return new Logo(input.readAllBytes(), type);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Could not read branding favicon", ex);
    }
  }

  @Transactional
  public BrandingView removeFavicon() {
    UUID organizationId = TenantContext.require();
    ensureRow(organizationId, Timestamp.from(clock.instant()));
    String key = currentFaviconKey(organizationId);
    jdbc.update(
        """
        update tenant_branding
           set favicon_object_key = null, favicon_content_type = null, updated_at = ?,
               updated_by = ?, version = version + 1
         where organization_id = ?
        """,
        Timestamp.from(clock.instant()),
        actor(),
        organizationId);
    if (key != null) storage.delete(key);
    return current();
  }

  @Transactional(readOnly = true)
  public Logo currentLogo() {
    UUID organizationId = TenantContext.require();
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select logo_object_key, logo_content_type from tenant_branding where organization_id = ?",
            organizationId);
    if (rows.isEmpty() || rows.get(0).get("logo_object_key") == null) {
      throw new ResourceNotFoundException("No branding logo is configured");
    }
    String key = (String) rows.get(0).get("logo_object_key");
    String type = (String) rows.get(0).get("logo_content_type");
    try (var input = storage.get(key)) {
      return new Logo(input.readAllBytes(), type);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Could not read branding logo", ex);
    }
  }

  @Transactional
  public BrandingView removeLogo() {
    UUID organizationId = TenantContext.require();
    ensureRow(organizationId, Timestamp.from(clock.instant()));
    String key = currentLogoKey(organizationId);
    jdbc.update(
        """
        update tenant_branding
           set logo_object_key = null, logo_content_type = null, updated_at = ?,
               updated_by = ?, version = version + 1
         where organization_id = ?
        """,
        Timestamp.from(clock.instant()),
        actor(),
        organizationId);
    if (key != null) storage.delete(key);
    audit.recordCurrentActor("TenantBranding", organizationId, "BRANDING_LOGO_REMOVED", Map.of());
    return current();
  }

  private void ensureRow(UUID organizationId, Timestamp now) {
    jdbc.update(
        """
        insert into tenant_branding
            (organization_id, primary_color, secondary_color, created_at, created_by,
             updated_at, updated_by)
        values (?, '#0B5CAD', '#00695C', ?, ?, ?, ?)
        on conflict (organization_id) do nothing
        """,
        organizationId,
        now,
        actor(),
        now,
        actor());
  }

  private String currentLogoKey(UUID organizationId) {
    List<String> keys =
        jdbc.queryForList(
            "select logo_object_key from tenant_branding where organization_id = ?",
            String.class,
            organizationId);
    return keys.isEmpty() ? null : keys.get(0);
  }

  private String currentFaviconKey(UUID organizationId) {
    List<String> keys =
        jdbc.queryForList(
            "select favicon_object_key from tenant_branding where organization_id = ?",
            String.class,
            organizationId);
    return keys.isEmpty() ? null : keys.get(0);
  }

  private String actor() {
    return currentUser.username();
  }

  private static void validate(BrandingCommand command) {
    if (command.displayName() == null || command.displayName().isBlank()) {
      throw new BusinessRuleException("Workspace display name is required");
    }
    if (command.primaryColor() == null || !command.primaryColor().matches("(?i)#[0-9a-f]{6}")) {
      throw new BusinessRuleException("Primary color must be a #RRGGBB value");
    }
    if (command.secondaryColor() == null || !command.secondaryColor().matches("(?i)#[0-9a-f]{6}")) {
      throw new BusinessRuleException("Secondary color must be a #RRGGBB value");
    }
    if (command.supportEmail() != null
        && !command.supportEmail().isBlank()
        && !command.supportEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new BusinessRuleException("Support email is invalid");
    }
    if (command.cornerRadius() != null
        && (command.cornerRadius() < 0 || command.cornerRadius() > 24)) {
      throw new BusinessRuleException("Corner radius must be between 0 and 24");
    }
    if (command.fontFamily() != null
        && !Set.of("INTER", "ROBOTO", "SYSTEM", "SERIF")
            .contains(command.fontFamily().toUpperCase())) {
      throw new BusinessRuleException("Font family is invalid");
    }
    if (command.uiDensity() != null
        && !Set.of("COMFORTABLE", "COMPACT").contains(command.uiDensity().toUpperCase())) {
      throw new BusinessRuleException("UI density is invalid");
    }
    validateLabel(command.requestLabel(), "Request label");
    validateLabel(command.orderLabel(), "Order label");
    validateLabel(command.vendorLabel(), "Vendor label");
    validateLabel(command.budgetLabel(), "Budget label");
    validateLabel(command.catalogLabel(), "Catalog label");
  }

  private static void validateLabel(String value, String field) {
    if (value != null && (value.isBlank() || value.length() > 40)) {
      throw new BusinessRuleException(field + " must be 1-40 characters");
    }
  }

  private static String label(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static java.time.Instant instant(Object value) {
    return value instanceof Timestamp timestamp ? timestamp.toInstant() : null;
  }

  public record BrandingView(
      UUID organizationId,
      String displayName,
      String primaryColor,
      String secondaryColor,
      String emailFromName,
      String supportEmail,
      boolean hasLogo,
      boolean hasFavicon,
      int cornerRadius,
      String fontFamily,
      String uiDensity,
      String requestLabel,
      String orderLabel,
      String vendorLabel,
      String budgetLabel,
      String catalogLabel,
      long version,
      java.time.Instant updatedAt) {}

  public record BrandingCommand(
      String displayName,
      String primaryColor,
      String secondaryColor,
      String emailFromName,
      String supportEmail,
      Integer cornerRadius,
      String fontFamily,
      String uiDensity,
      String requestLabel,
      String orderLabel,
      String vendorLabel,
      String budgetLabel,
      String catalogLabel) {}

  public record Logo(byte[] content, String contentType) {}
}
