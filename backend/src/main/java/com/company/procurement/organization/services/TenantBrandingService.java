package com.company.procurement.organization.services;

import com.company.procurement.audit.services.AuditEventService;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.TenantBranding;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.organization.repositories.TenantBrandingRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.storage.FileStoragePort;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages tenant branding assets, colors and terminology. */
@Service
public class TenantBrandingService {

  private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
  private static final Set<String> LOGO_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
  private static final long MAX_FAVICON_BYTES = 512L * 1024;
  private static final Set<String> FAVICON_TYPES =
      Set.of("image/png", "image/svg+xml", "image/x-icon", "image/vnd.microsoft.icon");

  private final TenantBrandingRepository brandings;
  private final OrganizationRepository organizations;
  private final FileStoragePort storage;
  private final AuditEventService audit;

  public TenantBrandingService(
      TenantBrandingRepository brandings,
      OrganizationRepository organizations,
      FileStoragePort storage,
      AuditEventService audit) {
    this.brandings = brandings;
    this.organizations = organizations;
    this.storage = storage;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public BrandingView current() {
    return current(TenantContext.require());
  }

  @Transactional(readOnly = true)
  public BrandingView current(UUID organizationId) {
    Organization organization = requireOrganization(organizationId);
    return view(organization, brandings.findById(organizationId).orElse(null));
  }

  @Transactional
  public BrandingView update(BrandingCommand command) {
    UUID organizationId = TenantContext.require();
    validate(command);
    TenantBranding branding = ensure(organizationId);
    branding.update(
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
        label(command.catalogLabel(), "Catalog items"));
    brandings.save(branding);
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
    TenantBranding branding = ensure(organizationId);
    String previous = branding.getLogoObjectKey();
    String extension =
        switch (type) {
          case "image/png" -> "png";
          case "image/webp" -> "webp";
          case "image/svg+xml" -> "svg";
          default -> "jpg";
        };
    String key = "branding/%s/logo-%s.%s".formatted(organizationId, UUID.randomUUID(), extension);
    storage.put(key, content, type);
    branding.setLogo(key, type);
    brandings.save(branding);
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
    TenantBranding branding = ensure(organizationId);
    String previous = branding.getFaviconObjectKey();
    String extension =
        switch (type) {
          case "image/png" -> "png";
          case "image/svg+xml" -> "svg";
          default -> "ico";
        };
    String key =
        "branding/%s/favicon-%s.%s".formatted(organizationId, UUID.randomUUID(), extension);
    storage.put(key, content, type);
    branding.setFavicon(key, type);
    brandings.save(branding);
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
    TenantBranding branding =
        brandings
            .findById(organizationId)
            .filter(value -> value.getFaviconObjectKey() != null)
            .orElseThrow(() -> new ResourceNotFoundException("No branding favicon is configured"));
    return read(branding.getFaviconObjectKey(), branding.getFaviconContentType(), "favicon");
  }

  @Transactional
  public BrandingView removeFavicon() {
    UUID organizationId = TenantContext.require();
    TenantBranding branding = ensure(organizationId);
    String key = branding.getFaviconObjectKey();
    branding.clearFavicon();
    brandings.save(branding);
    if (key != null) {
      storage.delete(key);
    }
    return current();
  }

  @Transactional(readOnly = true)
  public Logo currentLogo() {
    UUID organizationId = TenantContext.require();
    TenantBranding branding =
        brandings
            .findById(organizationId)
            .filter(value -> value.getLogoObjectKey() != null)
            .orElseThrow(() -> new ResourceNotFoundException("No branding logo is configured"));
    return read(branding.getLogoObjectKey(), branding.getLogoContentType(), "logo");
  }

  @Transactional
  public BrandingView removeLogo() {
    UUID organizationId = TenantContext.require();
    TenantBranding branding = ensure(organizationId);
    String key = branding.getLogoObjectKey();
    branding.clearLogo();
    brandings.save(branding);
    if (key != null) {
      storage.delete(key);
    }
    audit.recordCurrentActor("TenantBranding", organizationId, "BRANDING_LOGO_REMOVED", Map.of());
    return current();
  }

  private TenantBranding ensure(UUID organizationId) {
    return brandings
        .findById(organizationId)
        .orElseGet(() -> brandings.save(new TenantBranding(organizationId)));
  }

  private Organization requireOrganization(UUID organizationId) {
    return organizations
        .findById(organizationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Organization %s not found".formatted(organizationId)));
  }

  private Logo read(String key, String contentType, String asset) {
    try (var input = storage.get(key)) {
      return new Logo(input.readAllBytes(), contentType);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Could not read branding " + asset, ex);
    }
  }

  private static BrandingView view(Organization organization, TenantBranding branding) {
    if (branding == null) {
      return new BrandingView(
          organization.getId(),
          organization.getName(),
          "#0B5CAD",
          "#00695C",
          organization.getName(),
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
    return new BrandingView(
        organization.getId(),
        branding.getDisplayName() == null ? organization.getName() : branding.getDisplayName(),
        branding.getPrimaryColor(),
        branding.getSecondaryColor(),
        branding.getEmailFromName(),
        branding.getSupportEmail(),
        branding.getLogoObjectKey() != null,
        branding.getFaviconObjectKey() != null,
        branding.getCornerRadius(),
        branding.getFontFamily(),
        branding.getUiDensity(),
        branding.getRequestLabel(),
        branding.getOrderLabel(),
        branding.getVendorLabel(),
        branding.getBudgetLabel(),
        branding.getCatalogLabel(),
        branding.getVersion(),
        branding.getUpdatedAt());
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
      Instant updatedAt) {}

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
