package com.company.procurement.receiving.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "vendor_invoice")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class VendorInvoice extends TenantEntity {

  @Column(name = "purchase_order_id", nullable = false, updatable = false)
  private UUID purchaseOrderId;

  @Column(name = "po_number", nullable = false, updatable = false, length = 40)
  private String poNumber;

  @Column(name = "vendor_id", nullable = false, updatable = false)
  private UUID vendorId;

  @Column(name = "invoice_number", nullable = false, updatable = false, length = 100)
  private String invoiceNumber;

  @Column(name = "internal_reference", unique = true, length = 40)
  private String internalReference;

  @Column(name = "invoice_date", nullable = false, updatable = false)
  private LocalDate invoiceDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "currency", nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
  private BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal taxAmount;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private VendorInvoiceStatus status;

  @Column(name = "tolerance_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal tolerancePercent;

  @Column(name = "notes", length = 1000)
  private String notes;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approved_by", length = 200)
  private String approvedBy;

  @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal paidAmount = BigDecimal.ZERO;

  @Column(name = "resolution_note", length = 1000)
  private String resolutionNote;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by", length = 200)
  private String resolvedBy;

  @OneToMany(
      mappedBy = "invoice",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<VendorInvoiceLine> lines = new ArrayList<>();

  protected VendorInvoice() {
    // for JPA
  }

  public VendorInvoice(
      UUID organizationId,
      UUID purchaseOrderId,
      String poNumber,
      UUID vendorId,
      String invoiceNumber,
      String internalReference,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String currency,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal totalAmount,
      VendorInvoiceStatus status,
      BigDecimal tolerancePercent,
      String notes,
      List<VendorInvoiceLineDraft> drafts) {
    super(organizationId);
    this.purchaseOrderId = Objects.requireNonNull(purchaseOrderId, "purchaseOrderId");
    this.poNumber = Objects.requireNonNull(poNumber, "poNumber");
    this.vendorId = Objects.requireNonNull(vendorId, "vendorId");
    this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "invoiceNumber").trim();
    this.internalReference = internalReference;
    this.invoiceDate = Objects.requireNonNull(invoiceDate, "invoiceDate");
    this.dueDate = dueDate;
    this.currency = Objects.requireNonNull(currency, "currency");
    this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
    this.status = Objects.requireNonNull(status, "status");
    this.tolerancePercent = Objects.requireNonNull(tolerancePercent, "tolerancePercent");
    this.notes = notes;
    drafts.forEach(draft -> lines.add(new VendorInvoiceLine(organizationId, this, draft)));
  }

  public void approve(Instant now, String actor) {
    if (status != VendorInvoiceStatus.MISMATCHED && status != VendorInvoiceStatus.MATCHED) {
      throw new IllegalStateTransitionException(
          "Only matched or mismatched invoices can be approved; current status is " + status);
    }
    this.status = VendorInvoiceStatus.APPROVED;
    this.approvedAt = Objects.requireNonNull(now, "now");
    this.approvedBy = Objects.requireNonNull(actor, "actor");
  }

  public void reject(Instant now, String actor, String note) {
    if (status != VendorInvoiceStatus.MATCHED && status != VendorInvoiceStatus.MISMATCHED) {
      throw new IllegalStateTransitionException(
          "Only matched or mismatched invoices can be rejected; current status is " + status);
    }
    resolve(VendorInvoiceStatus.REJECTED, now, actor, note);
  }

  public void dispute(Instant now, String actor, String note) {
    if (status != VendorInvoiceStatus.MISMATCHED) {
      throw new IllegalStateTransitionException(
          "Only mismatched invoices can be disputed; current status is " + status);
    }
    resolve(VendorInvoiceStatus.DISPUTED, now, actor, note);
  }

  public void voidInvoice(Instant now, String actor, String note) {
    if (status == VendorInvoiceStatus.VOID || status == VendorInvoiceStatus.REJECTED) {
      throw new IllegalStateTransitionException("Invoice cannot be voided from status " + status);
    }
    resolve(VendorInvoiceStatus.VOID, now, actor, note);
  }

  private void resolve(VendorInvoiceStatus target, Instant now, String actor, String note) {
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("A resolution reason is required");
    }
    this.status = target;
    this.resolutionNote = note.trim();
    this.resolvedAt = Objects.requireNonNull(now, "now");
    this.resolvedBy = Objects.requireNonNull(actor, "actor");
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getResolvedBy() {
    return resolvedBy;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public String getPoNumber() {
    return poNumber;
  }

  public UUID getVendorId() {
    return vendorId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public String getInternalReference() {
    return internalReference;
  }

  public LocalDate getInvoiceDate() {
    return invoiceDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public VendorInvoiceStatus getStatus() {
    return status;
  }

  public BigDecimal getTolerancePercent() {
    return tolerancePercent;
  }

  public String getNotes() {
    return notes;
  }

  public BigDecimal getPaidAmount() {
    return paidAmount == null ? BigDecimal.ZERO : paidAmount;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public List<VendorInvoiceLine> getLines() {
    return lines;
  }
}
