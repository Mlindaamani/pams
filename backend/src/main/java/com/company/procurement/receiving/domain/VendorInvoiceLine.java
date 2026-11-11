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
@Table(name = "vendor_invoice_line")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class VendorInvoiceLine extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "vendor_invoice_id", nullable = false, updatable = false)
  private VendorInvoice invoice;

  @Column(name = "purchase_order_id", nullable = false, updatable = false)
  private UUID purchaseOrderId;

  @Column(name = "purchase_order_line_id", nullable = false, updatable = false)
  private UUID purchaseOrderLineId;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal lineTotal;

  @Column(name = "matched", nullable = false)
  private boolean matched;

  @Column(name = "price_variance_percent", nullable = false, precision = 9, scale = 4)
  private BigDecimal priceVariancePercent;

  @Column(name = "match_message", length = 500)
  private String matchMessage;

  protected VendorInvoiceLine() {
    // for JPA
  }

  VendorInvoiceLine(UUID organizationId, VendorInvoice invoice, VendorInvoiceLineDraft draft) {
    super(organizationId);
    this.invoice = Objects.requireNonNull(invoice, "invoice");
    this.purchaseOrderId = Objects.requireNonNull(invoice.getPurchaseOrderId(), "purchaseOrderId");
    this.purchaseOrderLineId = Objects.requireNonNull(draft.purchaseOrderLineId(), "lineId");
    this.description = Objects.requireNonNull(draft.description(), "description");
    this.unit = Objects.requireNonNull(draft.unit(), "unit");
    this.quantity = Objects.requireNonNull(draft.quantity(), "quantity");
    this.unitPrice = Objects.requireNonNull(draft.unitPrice(), "unitPrice");
    this.lineTotal = Objects.requireNonNull(draft.lineTotal(), "lineTotal");
    this.matched = draft.matched();
    this.priceVariancePercent =
        Objects.requireNonNull(draft.priceVariancePercent(), "priceVariancePercent");
    this.matchMessage = draft.matchMessage();
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
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

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public boolean isMatched() {
    return matched;
  }

  public BigDecimal getPriceVariancePercent() {
    return priceVariancePercent;
  }

  public String getMatchMessage() {
    return matchMessage;
  }
}
