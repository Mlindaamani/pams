package com.company.procurement.request.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "purchase_request")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** Aggregate root for a purchase request and its line items. */
public class PurchaseRequest extends SoftDeletableEntity {

  @Column(name = "request_number", nullable = false, updatable = false, length = 40)
  private String requestNumber;

  @Column(name = "requester_subject", nullable = false, updatable = false, length = 200)
  private String requesterSubject;

  @Column(name = "requester_name", nullable = false, updatable = false, length = 200)
  private String requesterName;

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "title", nullable = false, length = 300)
  private String title;

  @Column(name = "justification", nullable = false, length = 2000)
  private String justification;

  @Column(name = "needed_by")
  private LocalDate neededBy;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private RequestStatus status;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "budget_id")
  private UUID budgetId;

  @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal reservedAmount = BigDecimal.ZERO;

  @Column(name = "approval_policy_id")
  private UUID approvalPolicyId;

  @Column(name = "approval_policy_version")
  private Integer approvalPolicyVersion;

  @Column(name = "budget_reserved", nullable = false)
  private boolean budgetReserved;

  @Column(name = "budget_exception_requested", nullable = false)
  private boolean budgetExceptionRequested;

  @Column(name = "budget_exception", nullable = false)
  private boolean budgetException;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @OneToMany(
      mappedBy = "request",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = jakarta.persistence.FetchType.LAZY)
  @OrderBy("position ASC")
  private List<PurchaseRequestLine> lines = new ArrayList<>();

  protected PurchaseRequest() {
    // for JPA
  }

  public PurchaseRequest(
      UUID organizationId,
      String requestNumber,
      String requesterSubject,
      String requesterName,
      UUID departmentId,
      String title,
      String justification,
      LocalDate neededBy,
      String currency,
      boolean budgetExceptionRequested) {
    super(organizationId);
    this.requestNumber = requireText(requestNumber, "requestNumber");
    this.requesterSubject = requireText(requesterSubject, "requesterSubject");
    this.requesterName = requireText(requesterName, "requesterName");
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
    this.status = RequestStatus.DRAFT;
    updateHeader(title, justification, neededBy, departmentId, currency);
    this.budgetExceptionRequested = budgetExceptionRequested;
  }

  public void updateHeader(
      String title, String justification, LocalDate neededBy, UUID departmentId, String currency) {
    assertEditable();
    this.title = requireText(title, "title");
    this.justification = requireText(justification, "justification");
    this.neededBy = neededBy;
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
    this.currency = requireCurrency(currency);
  }

  public void replaceLines(List<PurchaseRequestLineDraft> drafts) {
    assertEditable();
    lines.clear();
    int position = 0;
    for (PurchaseRequestLineDraft draft : drafts) {
      lines.add(
          new PurchaseRequestLine(
              getOrganizationId(),
              this,
              draft.description(),
              draft.quantity(),
              draft.unit(),
              draft.unitPrice(),
              draft.catalogItemId(),
              draft.vendorId(),
              draft.nonCatalog(),
              draft.notes(),
              draft.catalogReviewStatus(),
              position++));
    }
    recalculateTotal();
  }

  public void submit(Instant now) {
    if (!status.isEditable()) {
      throw new IllegalStateTransitionException(
          "Only draft or change-requested requests can be submitted");
    }
    if (lines.isEmpty()) {
      throw new BusinessRuleException("A request needs at least one line item before submission");
    }
    transitionTo(RequestStatus.SUBMITTED);
    this.submittedAt = Objects.requireNonNull(now, "now");
  }

  public void cancel() {
    if (!status.canTransitionTo(RequestStatus.CANCELLED)) {
      throw new IllegalStateTransitionException("Request cannot be cancelled from " + status);
    }
    transitionTo(RequestStatus.CANCELLED);
  }

  public void markBudgetReserved(UUID budgetId, BigDecimal amount) {
    this.budgetId = Objects.requireNonNull(budgetId, "budgetId");
    this.reservedAmount = Objects.requireNonNull(amount, "amount");
    this.budgetReserved = true;
    this.budgetException = false;
  }

  public void markBudgetException() {
    this.budgetId = null;
    this.reservedAmount = BigDecimal.ZERO;
    this.budgetReserved = false;
    this.budgetException = true;
  }

  public void clearBudgetReservation() {
    this.budgetId = null;
    this.reservedAmount = BigDecimal.ZERO;
    this.budgetReserved = false;
  }

  public void attachApprovalPolicy(UUID policyId, int policyVersion) {
    this.approvalPolicyId = Objects.requireNonNull(policyId, "policyId");
    this.approvalPolicyVersion = policyVersion;
  }

  public void advanceToInApproval() {
    transitionTo(RequestStatus.IN_APPROVAL);
  }

  public void approve() {
    transitionTo(RequestStatus.APPROVED);
  }

  public void reject() {
    transitionTo(RequestStatus.REJECTED);
  }

  public void requestChanges() {
    transitionTo(RequestStatus.CHANGES_REQUESTED);
  }

  public void convertToPurchaseOrder() {
    transitionTo(RequestStatus.CONVERTED_TO_PO);
  }

  public void reopenAfterPurchaseOrderCancellation() {
    transitionTo(RequestStatus.APPROVED);
  }

  public boolean isOwnedBy(String subject) {
    return requesterSubject.equals(subject);
  }

  public void assertEditable() {
    if (!status.isEditable()) {
      throw new IllegalStateTransitionException("Request is not editable in status " + status);
    }
  }

  private void transitionTo(RequestStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new IllegalStateTransitionException(
          "Illegal transition from %s to %s".formatted(status, target));
    }
    this.status = target;
  }

  private void recalculateTotal() {
    this.totalAmount =
        lines.stream()
            .map(PurchaseRequestLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(4, RoundingMode.HALF_UP);
  }

  public String getRequestNumber() {
    return requestNumber;
  }

  public String getRequesterSubject() {
    return requesterSubject;
  }

  public String getRequesterName() {
    return requesterName;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  public String getTitle() {
    return title;
  }

  public String getJustification() {
    return justification;
  }

  public LocalDate getNeededBy() {
    return neededBy;
  }

  public String getCurrency() {
    return currency;
  }

  public RequestStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public UUID getBudgetId() {
    return budgetId;
  }

  public BigDecimal getReservedAmount() {
    return reservedAmount;
  }

  public UUID getApprovalPolicyId() {
    return approvalPolicyId;
  }

  public Integer getApprovalPolicyVersion() {
    return approvalPolicyVersion;
  }

  public boolean isBudgetReserved() {
    return budgetReserved;
  }

  public boolean isBudgetExceptionRequested() {
    return budgetExceptionRequested;
  }

  public boolean isBudgetException() {
    return budgetException;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public List<PurchaseRequestLine> getLines() {
    return lines;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }

  private static String requireCurrency(String value) {
    Objects.requireNonNull(value, "currency");
    String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
    if (normalized.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter code");
    }
    return normalized;
  }
}
