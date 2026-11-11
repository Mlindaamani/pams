package com.company.procurement.catalog.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "catalog_item")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class CatalogItem extends SoftDeletableEntity {

  @Column(name = "sku", nullable = false, length = 100)
  private String sku;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "default_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal defaultPrice;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "category", length = 100)
  private String category;

  @Column(name = "default_vendor_id")
  private UUID defaultVendorId;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected CatalogItem() {
    // for JPA
  }

  public CatalogItem(
      UUID organizationId,
      String sku,
      String name,
      String unit,
      BigDecimal defaultPrice,
      String currency,
      String category,
      UUID defaultVendorId) {
    super(organizationId);
    this.sku = requireText(sku, "sku");
    this.active = true;
    updateDetails(name, unit, defaultPrice, currency, category, defaultVendorId);
  }

  public void updateDetails(
      String name,
      String unit,
      BigDecimal defaultPrice,
      String currency,
      String category,
      UUID defaultVendorId) {
    this.name = requireText(name, "name");
    this.unit = requireText(unit, "unit");
    this.defaultPrice = requireNonNegative(defaultPrice);
    this.currency = requireCurrency(currency);
    this.category = trimToNull(category);
    this.defaultVendorId = defaultVendorId;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public String getUnit() {
    return unit;
  }

  public BigDecimal getDefaultPrice() {
    return defaultPrice;
  }

  public String getCurrency() {
    return currency;
  }

  public String getCategory() {
    return category;
  }

  public UUID getDefaultVendorId() {
    return defaultVendorId;
  }

  public boolean isActive() {
    return active;
  }

  private static BigDecimal requireNonNegative(BigDecimal value) {
    Objects.requireNonNull(value, "defaultPrice");
    if (value.signum() < 0) {
      throw new IllegalArgumentException("defaultPrice must not be negative");
    }
    return value;
  }

  private static String requireCurrency(String value) {
    Objects.requireNonNull(value, "currency");
    String trimmed = value.trim();
    if (trimmed.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter code");
    }
    return trimmed;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
