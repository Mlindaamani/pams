package com.company.procurement.purchaseorder.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "purchase_order")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** Aggregate root for a purchase order and its issued/received lifecycle. */
public class PurchaseOrder extends TenantEntity {

  @Column(name = "purchase_request_id", nullable = false, updatable = false)
  private UUID purchaseRequestId;

  @Column(name = "requester_subject", nullable = false, updatable = false, length = 200)
  private String requesterSubject;

  @Column(name = "po_number", nullable = false, updatable = false, length = 40)
  private String poNumber;

  @Column(name = "vendor_id", nullable = false, updatable = false)
  private UUID vendorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private PurchaseOrderStatus status;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  @Column(name = "payment_terms", length = 500)
  private String paymentTerms;

  @Column(name = "delivery_date")
  private LocalDate deliveryDate;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "budget_id")
  private UUID budgetId;

  @Column(name = "budget_reserved_amount", precision = 19, scale = 4)
  private BigDecimal budgetReservedAmount;

  @OneToMany(
      mappedBy = "purchaseOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("sequence ASC")
  private List<PurchaseOrderLine> lines = new ArrayList<>();

  protected PurchaseOrder() {
    // for JPA
  }

  public PurchaseOrder(
      UUID organizationId,
      UUID purchaseRequestId,
      String requesterSubject,
      String poNumber,
      UUID vendorId,
      String currency,
      BigDecimal totalAmount,
      String paymentTerms,
      LocalDate deliveryDate,
      UUID budgetId,
      BigDecimal budgetReservedAmount,
      List<PurchaseOrderLineDraft> drafts) {
    super(organizationId);
    this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
    this.requesterSubject = Objects.requireNonNull(requesterSubject, "requesterSubject");
    this.poNumber = Objects.requireNonNull(poNumber, "poNumber");
    this.vendorId = Objects.requireNonNull(vendorId, "vendorId");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
    this.paymentTerms = paymentTerms;
    this.deliveryDate = deliveryDate;
    this.budgetId = budgetId;
    this.budgetReservedAmount = budgetReservedAmount;
    this.status = PurchaseOrderStatus.DRAFT;
    int sequence = 1;
    for (PurchaseOrderLineDraft draft : drafts) {
      lines.add(
          new PurchaseOrderLine(
              organizationId,
              this,
              sequence++,
              draft.description(),
              draft.quantity(),
              draft.unit(),
              draft.unitPrice()));
    }
  }

  public void updateDraft(String paymentTerms, LocalDate deliveryDate) {
    if (status != PurchaseOrderStatus.DRAFT) {
      throw new IllegalStateTransitionException("Only draft purchase orders can be edited");
    }
    this.paymentTerms = paymentTerms;
    this.deliveryDate = deliveryDate;
  }

  public void submitForApproval() {
    if (status != PurchaseOrderStatus.DRAFT) {
      throw new IllegalStateTransitionException(
          "Only draft purchase orders can be submitted for approval");
    }
    this.status = PurchaseOrderStatus.PENDING_APPROVAL;
  }

  public void approveForIssue() {
    if (status != PurchaseOrderStatus.PENDING_APPROVAL) {
      throw new IllegalStateTransitionException(
          "Only purchase orders pending approval can be approved");
    }
    this.status = PurchaseOrderStatus.APPROVED;
  }

  public void rejectApproval() {
    if (status != PurchaseOrderStatus.PENDING_APPROVAL) {
      throw new IllegalStateTransitionException(
          "Only purchase orders pending approval can be rejected");
    }
    this.status = PurchaseOrderStatus.REJECTED;
  }

  public void requestApprovalChanges() {
    if (status != PurchaseOrderStatus.PENDING_APPROVAL) {
      throw new IllegalStateTransitionException(
          "Only purchase orders pending approval can be returned for changes");
    }
    this.status = PurchaseOrderStatus.DRAFT;
  }

  public void issue(Instant now) {
    if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.APPROVED) {
      throw new IllegalStateTransitionException(
          "Only draft or approved purchase orders can be issued");
    }
    this.status = PurchaseOrderStatus.ISSUED;
    this.issuedAt = Objects.requireNonNull(now, "now");
  }

  public void recordReceipt(Map<UUID, BigDecimal> receivedByLine) {
    if (status != PurchaseOrderStatus.ISSUED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
      throw new IllegalStateTransitionException(
          "Goods cannot be received against a purchase order in status " + status);
    }
    if (receivedByLine == null || receivedByLine.isEmpty()) {
      throw new BusinessRuleException("At least one purchase order line must be received");
    }
    receivedByLine.forEach(
        (lineId, quantity) -> {
          PurchaseOrderLine line =
              lines.stream()
                  .filter(candidate -> candidate.getId().equals(lineId))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new BusinessRuleException(
                              "Purchase order line %s does not belong to this order"
                                  .formatted(lineId)));
          line.receive(quantity);
        });
    boolean fullyReceived =
        lines.stream()
            .allMatch(line -> line.getReceivedQuantity().compareTo(line.getQuantity()) >= 0);
    status = fullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
  }

  public void cancel() {
    if (status == PurchaseOrderStatus.CANCELLED
        || status == PurchaseOrderStatus.CLOSED
        || status == PurchaseOrderStatus.RECEIVED
        || status == PurchaseOrderStatus.REJECTED) {
      throw new IllegalStateTransitionException(
          "Purchase order cannot be cancelled from " + status);
    }
    this.status = PurchaseOrderStatus.CANCELLED;
  }

  public UUID getPurchaseRequestId() {
    return purchaseRequestId;
  }

  public String getRequesterSubject() {
    return requesterSubject;
  }

  public String getPoNumber() {
    return poNumber;
  }

  public UUID getVendorId() {
    return vendorId;
  }

  public PurchaseOrderStatus getStatus() {
    return status;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public LocalDate getDeliveryDate() {
    return deliveryDate;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public UUID getBudgetId() {
    return budgetId;
  }

  public BigDecimal getBudgetReservedAmount() {
    return budgetReservedAmount;
  }

  public List<PurchaseOrderLine> getLines() {
    return lines;
  }
}
