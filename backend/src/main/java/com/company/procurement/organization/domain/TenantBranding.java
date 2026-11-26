package com.company.procurement.organization.domain;

import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Tenant workspace identity, appearance and terminology. */
@Entity
@Table(name = "tenant_branding")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
@EntityListeners(AuditingEntityListener.class)
public class TenantBranding {

  @Id
  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "display_name", length = 200)
  private String displayName;

  @Column(name = "logo_object_key", length = 300)
  private String logoObjectKey;

  @Column(name = "logo_content_type", length = 100)
  private String logoContentType;

  @Column(name = "favicon_object_key", length = 300)
  private String faviconObjectKey;

  @Column(name = "favicon_content_type", length = 100)
  private String faviconContentType;

  @Column(name = "primary_color", nullable = false, length = 7)
  private String primaryColor;

  @Column(name = "secondary_color", nullable = false, length = 7)
  private String secondaryColor;

  @Column(name = "email_from_name", length = 200)
  private String emailFromName;

  @Column(name = "support_email", length = 320)
  private String supportEmail;

  @Column(name = "corner_radius", nullable = false)
  private int cornerRadius;

  @Column(name = "font_family", nullable = false, length = 30)
  private String fontFamily;

  @Column(name = "ui_density", nullable = false, length = 20)
  private String uiDensity;

  @Column(name = "request_label", nullable = false, length = 40)
  private String requestLabel;

  @Column(name = "order_label", nullable = false, length = 40)
  private String orderLabel;

  @Column(name = "vendor_label", nullable = false, length = 40)
  private String vendorLabel;

  @Column(name = "budget_label", nullable = false, length = 40)
  private String budgetLabel;

  @Column(name = "catalog_label", nullable = false, length = 40)
  private String catalogLabel;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  @CreatedBy
  private String createdBy;

  @Column(name = "updated_at", nullable = false)
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "updated_by", nullable = false, length = 100)
  @LastModifiedBy
  private String updatedBy;

  @Version
  @Column(nullable = false)
  private long version;

  protected TenantBranding() {
    // for JPA
  }

  public TenantBranding(UUID organizationId) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.primaryColor = "#0B5CAD";
    this.secondaryColor = "#00695C";
    this.cornerRadius = 10;
    this.fontFamily = "INTER";
    this.uiDensity = "COMFORTABLE";
    this.requestLabel = "Requests";
    this.orderLabel = "Purchase orders";
    this.vendorLabel = "Vendors";
    this.budgetLabel = "Budgets";
    this.catalogLabel = "Catalog items";
  }

  public void update(
      String displayName,
      String primaryColor,
      String secondaryColor,
      String emailFromName,
      String supportEmail,
      int cornerRadius,
      String fontFamily,
      String uiDensity,
      String requestLabel,
      String orderLabel,
      String vendorLabel,
      String budgetLabel,
      String catalogLabel) {
    this.displayName = displayName;
    this.primaryColor = primaryColor;
    this.secondaryColor = secondaryColor;
    this.emailFromName = emailFromName;
    this.supportEmail = supportEmail;
    this.cornerRadius = cornerRadius;
    this.fontFamily = fontFamily;
    this.uiDensity = uiDensity;
    this.requestLabel = requestLabel;
    this.orderLabel = orderLabel;
    this.vendorLabel = vendorLabel;
    this.budgetLabel = budgetLabel;
    this.catalogLabel = catalogLabel;
  }

  public void setLogo(String objectKey, String contentType) {
    this.logoObjectKey = Objects.requireNonNull(objectKey, "objectKey");
    this.logoContentType = Objects.requireNonNull(contentType, "contentType");
  }

  public void clearLogo() {
    this.logoObjectKey = null;
    this.logoContentType = null;
  }

  public void setFavicon(String objectKey, String contentType) {
    this.faviconObjectKey = Objects.requireNonNull(objectKey, "objectKey");
    this.faviconContentType = Objects.requireNonNull(contentType, "contentType");
  }

  public void clearFavicon() {
    this.faviconObjectKey = null;
    this.faviconContentType = null;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getLogoObjectKey() {
    return logoObjectKey;
  }

  public String getLogoContentType() {
    return logoContentType;
  }

  public String getFaviconObjectKey() {
    return faviconObjectKey;
  }

  public String getFaviconContentType() {
    return faviconContentType;
  }

  public String getPrimaryColor() {
    return primaryColor;
  }

  public String getSecondaryColor() {
    return secondaryColor;
  }

  public String getEmailFromName() {
    return emailFromName;
  }

  public String getSupportEmail() {
    return supportEmail;
  }

  public int getCornerRadius() {
    return cornerRadius;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public String getUiDensity() {
    return uiDensity;
  }

  public String getRequestLabel() {
    return requestLabel;
  }

  public String getOrderLabel() {
    return orderLabel;
  }

  public String getVendorLabel() {
    return vendorLabel;
  }

  public String getBudgetLabel() {
    return budgetLabel;
  }

  public String getCatalogLabel() {
    return catalogLabel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public long getVersion() {
    return version;
  }
}
