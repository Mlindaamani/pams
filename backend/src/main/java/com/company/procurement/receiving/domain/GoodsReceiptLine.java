package com.company.procurement.receiving.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "goods_receipt_line")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class GoodsReceiptLine extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
  private GoodsReceipt receipt;

  @Column(name = "purchase_order_line_id", nullable = false, updatable = false)
  private UUID purchaseOrderLineId;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  protected GoodsReceiptLine() {
    // for JPA
  }

  GoodsReceiptLine(
      UUID organizationId,
      GoodsReceipt receipt,
      UUID purchaseOrderLineId,
      String description,
      String unit,
      BigDecimal quantity) {
    super(organizationId);
    this.receipt = Objects.requireNonNull(receipt, "receipt");
    this.purchaseOrderLineId = Objects.requireNonNull(purchaseOrderLineId, "purchaseOrderLineId");
    this.description = Objects.requireNonNull(description, "description");
    this.unit = Objects.requireNonNull(unit, "unit");
    this.quantity = Objects.requireNonNull(quantity, "quantity");
  }

  public UUID getPurchaseOrderLineId() {
    return purchaseOrderLineId;
  }

  public String getDescription() {
    return description;
  }

  public String getUnit() {
    return unit;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }
}
