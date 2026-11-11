package com.company.procurement.purchaseorder.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "purchase_order_line")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class PurchaseOrderLine extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
  private PurchaseOrder purchaseOrder;

  @Column(name = "sequence", nullable = false)
  private int sequence;

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

  @Column(name = "received_quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal receivedQuantity = BigDecimal.ZERO;

  protected PurchaseOrderLine() {
    // for JPA
  }

  PurchaseOrderLine(
      java.util.UUID organizationId,
      PurchaseOrder purchaseOrder,
      int sequence,
      String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitPrice) {
    super(organizationId);
    this.purchaseOrder = Objects.requireNonNull(purchaseOrder, "purchaseOrder");
    this.sequence = sequence;
    this.description = Objects.requireNonNull(description, "description");
    this.quantity = Objects.requireNonNull(quantity, "quantity");
    this.unit = Objects.requireNonNull(unit, "unit");
    this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    this.lineTotal = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
  }

  void receive(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) {
      throw new BusinessRuleException("Receipt quantity must be greater than zero");
    }
    BigDecimal updated = receivedQuantity.add(quantity);
    if (updated.compareTo(this.quantity) > 0) {
      throw new BusinessRuleException(
          "Cannot receive more than ordered for line %d (%s %s)"
              .formatted(sequence, this.quantity.stripTrailingZeros().toPlainString(), unit));
    }
    this.receivedQuantity = updated;
  }

  public int getSequence() {
    return sequence;
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

  public BigDecimal getReceivedQuantity() {
    return receivedQuantity;
  }

  public BigDecimal getRemainingQuantity() {
    return quantity.subtract(receivedQuantity);
  }
}
