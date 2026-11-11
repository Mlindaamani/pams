package com.company.procurement.request.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "purchase_request_line")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class PurchaseRequestLine extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_request_id", nullable = false, updatable = false)
  private PurchaseRequest request;

  @Column(name = "catalog_item_id")
  private UUID catalogItemId;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal lineTotal;

  @Column(name = "position", nullable = false)
  private int position;

  protected PurchaseRequestLine() {
    // for JPA
  }

  PurchaseRequestLine(
      UUID organizationId,
      PurchaseRequest request,
      String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitPrice,
      UUID catalogItemId,
      int position) {
    super(organizationId);
    this.request = Objects.requireNonNull(request, "request");
    this.description = requireText(description, "description");
    this.quantity = requirePositive(quantity, "quantity");
    this.unit = requireText(unit, "unit");
    this.unitPrice = requireNonNegative(unitPrice, "unitPrice");
    this.catalogItemId = catalogItemId;
    this.position = position;
    this.lineTotal = this.quantity.multiply(this.unitPrice).setScale(4, RoundingMode.HALF_UP);
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public String getUnit() {
    return unit;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public int getPosition() {
    return position;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  private static BigDecimal requirePositive(BigDecimal value, String field) {
    Objects.requireNonNull(value, field);
    if (value.signum() <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return value;
  }

  private static BigDecimal requireNonNegative(BigDecimal value, String field) {
    Objects.requireNonNull(value, field);
    if (value.signum() < 0) {
      throw new IllegalArgumentException(field + " must not be negative");
    }
    return value;
  }
}
