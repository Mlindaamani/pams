package com.company.procurement.purchaseorder.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.budget.application.BudgetService;
import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderFilter;
import com.company.procurement.purchaseorder.domain.PurchaseOrderLineDraft;
import com.company.procurement.purchaseorder.domain.PurchaseOrderNumberGenerator;
import com.company.procurement.purchaseorder.domain.PurchaseOrderRepository;
import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.company.procurement.request.application.PurchaseRequestLineSnapshot;
import com.company.procurement.request.application.PurchaseRequestService;
import com.company.procurement.request.application.PurchaseRequestSnapshot;
import com.company.procurement.shared.domain.ReportingDataChangedEvent;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.outbox.OutboxService;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.vendor.application.VendorService;
import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {

  private final PurchaseOrderRepository orders;
  private final PurchaseRequestService requests;
  private final VendorService vendors;
  private final BudgetService budgets;
  private final PurchaseOrderNumberGenerator numbers;
  private final PurchaseOrderApprovalService approvals;
  private final OutboxService outbox;
  private final AuditEventService audit;
  private final ApplicationEventPublisher events;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PurchaseOrderService(
      PurchaseOrderRepository orders,
      PurchaseRequestService requests,
      VendorService vendors,
      BudgetService budgets,
      PurchaseOrderNumberGenerator numbers,
      PurchaseOrderApprovalService approvals,
      OutboxService outbox,
      AuditEventService audit,
      ApplicationEventPublisher events,
      JdbcTemplate jdbc,
      Clock clock) {
    this.orders = orders;
    this.requests = requests;
    this.vendors = vendors;
    this.budgets = budgets;
    this.numbers = numbers;
    this.approvals = approvals;
    this.outbox = outbox;
    this.audit = audit;
    this.events = events;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public PurchaseOrder create(UUID requestId, CreatePurchaseOrderCommand command) {
    UUID organizationId = TenantContext.require();
    if (orders
        .findActiveByOrganizationIdAndPurchaseRequestId(organizationId, requestId)
        .isPresent()) {
      throw new ConflictException("A purchase order already exists for this request");
    }
    PurchaseRequestSnapshot snapshot = requests.snapshotForPurchaseOrder(requestId);
    java.util.Set<UUID> requestVendors =
        snapshot.lines().stream()
            .map(PurchaseRequestLineSnapshot::vendorId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    if (requestVendors.size() == 1 && !requestVendors.contains(command.vendorId())) {
      throw new BusinessRuleException(
          "The purchase order vendor must match the vendor locked on the request items");
    }
    Vendor vendor = vendors.get(command.vendorId());
    if (vendor.getStatus() != VendorStatus.ACTIVE) {
      throw new BusinessRuleException("Only active vendors can be selected for purchase orders");
    }

    int fiscalYear = LocalDate.now(clock).getYear();
    String number = numbers.nextNumber(organizationId, fiscalYear);
    List<PurchaseOrderLineDraft> lines =
        snapshot.lines().stream()
            .map(
                line ->
                    new PurchaseOrderLineDraft(
                        line.description(), line.quantity(), line.unit(), line.unitPrice()))
            .toList();
    PurchaseOrder order =
        new PurchaseOrder(
            organizationId,
            requestId,
            snapshot.requesterSubject(),
            number,
            vendor.getId(),
            snapshot.currency(),
            snapshot.totalAmount(),
            command.paymentTerms(),
            command.deliveryDate(),
            snapshot.budgetId(),
            snapshot.reservedAmount(),
            lines);
    requests.markConvertedToPurchaseOrder(requestId);
    PurchaseOrder saved = orders.save(order);
    orders.flush();
    saved = approvals.startIfConfigured(saved);
    audit.recordCurrentActor(
        "purchase_order",
        saved.getId(),
        "purchase_order_created",
        Map.of("requestId", requestId.toString(), "poNumber", saved.getPoNumber()));
    outbox.enqueue(
        OutboxEventTypes.REQUEST_CONVERTED_TO_PURCHASE_ORDER,
        "purchase_request",
        requestId,
        Map.of(
            "requestId", requestId.toString(),
            "poId", saved.getId().toString(),
            "poNumber", saved.getPoNumber()));
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
    return saved;
  }

  @Transactional
  public PurchaseOrder updateDraft(
      UUID id, long expectedVersion, UpdatePurchaseOrderCommand command) {
    PurchaseOrder order = getDetail(id);
    assertVersion(order, expectedVersion);
    order.updateDraft(command.paymentTerms(), command.deliveryDate());
    PurchaseOrder saved = orders.save(order);
    audit.recordCurrentActor("purchase_order", saved.getId(), "purchase_order_updated", Map.of());
    return saved;
  }

  @Transactional
  public PurchaseOrder issue(UUID id, long expectedVersion) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = getDetail(id);
    assertVersion(order, expectedVersion);
    approvals.requireIssueAllowed(id, order);
    Vendor vendor = vendors.get(order.getVendorId());
    if (vendor.getStatus() != VendorStatus.ACTIVE) {
      throw new BusinessRuleException("Only active vendors can receive issued purchase orders");
    }
    order.issue(clock.instant());
    if (order.getBudgetId() != null
        && order.getBudgetReservedAmount() != null
        && order.getBudgetReservedAmount().signum() > 0) {
      budgets.convertToCommitted(
          organizationId, order.getBudgetId(), order.getBudgetReservedAmount());
    }
    PurchaseOrder saved = orders.save(order);

    outbox.enqueue(
        OutboxEventTypes.PURCHASE_ORDER_ISSUED,
        "purchase_order",
        saved.getId(),
        Map.of(
            "poId", saved.getId().toString(),
            "requestId", saved.getPurchaseRequestId().toString(),
            "poNumber", saved.getPoNumber(),
            "vendorId", saved.getVendorId().toString(),
            "vendorName", vendor.getName(),
            "vendorEmail", vendor.getContactEmail() == null ? "" : vendor.getContactEmail(),
            "requesterSubject", saved.getRequesterSubject(),
            "total", saved.getTotalAmount().toPlainString(),
            "currency", saved.getCurrency()));
    audit.recordCurrentActor(
        "purchase_order",
        saved.getId(),
        "purchase_order_issued",
        Map.of(
            "poNumber", saved.getPoNumber(), "requestId", saved.getPurchaseRequestId().toString()));
    return saved;
  }

  @Transactional
  public PurchaseOrder cancel(UUID id, long expectedVersion) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = getDetail(id);
    assertVersion(order, expectedVersion);
    if (order.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED
        || order.getStatus() == PurchaseOrderStatus.RECEIVED) {
      throw new BusinessRuleException(
          "A purchase order with recorded receipts cannot be cancelled");
    }
    if (hasInvoices(organizationId, id)) {
      throw new BusinessRuleException(
          "A purchase order with invoices cannot be cancelled; resolve or void invoices first");
    }
    if (order.getStatus() == PurchaseOrderStatus.ISSUED
        && order.getBudgetId() != null
        && order.getBudgetReservedAmount() != null) {
      budgets.releaseCommitted(
          organizationId, order.getBudgetId(), order.getBudgetReservedAmount());
    }
    PurchaseOrderStatus previous = order.getStatus();
    approvals.cancelForPurchaseOrder(id);
    order.cancel();
    PurchaseOrder saved = orders.save(order);
    if (previous == PurchaseOrderStatus.DRAFT
        || previous == PurchaseOrderStatus.PENDING_APPROVAL
        || previous == PurchaseOrderStatus.APPROVED) {
      requests.reopenAfterPurchaseOrderCancellation(saved.getPurchaseRequestId());
    }
    audit.recordCurrentActor(
        "purchase_order",
        saved.getId(),
        "purchase_order_cancelled",
        Map.of("previousStatus", previous.name()));
    outbox.enqueue(
        OutboxEventTypes.PURCHASE_ORDER_CANCELLED,
        "purchase_order",
        saved.getId(),
        Map.of(
            "poId", saved.getId().toString(),
            "poNumber", saved.getPoNumber(),
            "previousStatus", previous.name()));
    return saved;
  }

  private boolean hasInvoices(UUID organizationId, UUID purchaseOrderId) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from vendor_invoice where organization_id = ? and purchase_order_id = ?",
            Long.class,
            organizationId,
            purchaseOrderId);
    return count != null && count > 0;
  }

  @Transactional
  public ReceiptApplicationResult recordReceipt(
      UUID id, long expectedVersion, Map<UUID, java.math.BigDecimal> receivedByLine) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order =
        orders
            .findForUpdate(id, organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Purchase order %s not found".formatted(id)));
    assertVersion(order, expectedVersion);
    order.recordReceipt(receivedByLine);
    PurchaseOrder saved = orders.save(order);
    return new ReceiptApplicationResult(
        saved.getId(),
        saved.getStatus(),
        saved.getVersion(),
        saved.getLines().stream()
            .map(
                line ->
                    new ReceiptApplicationResult.Line(
                        line.getId(),
                        line.getDescription(),
                        line.getUnit(),
                        line.getQuantity(),
                        line.getReceivedQuantity()))
            .toList());
  }

  @Transactional
  public InvoiceMatchData invoiceMatchDataForUpdate(UUID id) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order =
        orders
            .findForUpdate(id, organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Purchase order %s not found".formatted(id)));
    if (order.getStatus() != PurchaseOrderStatus.ISSUED
        && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED
        && order.getStatus() != PurchaseOrderStatus.RECEIVED) {
      throw new IllegalStateTransitionException(
          "Invoices cannot be recorded for a purchase order in status " + order.getStatus());
    }
    return new InvoiceMatchData(
        order.getId(),
        order.getPoNumber(),
        order.getVendorId(),
        order.getCurrency(),
        order.getStatus(),
        order.getLines().stream()
            .map(
                line ->
                    new InvoiceMatchData.Line(
                        line.getId(),
                        line.getDescription(),
                        line.getUnit(),
                        line.getQuantity(),
                        line.getReceivedQuantity(),
                        line.getUnitPrice()))
            .toList());
  }

  @Transactional(readOnly = true)
  public void requireVisible(UUID id) {
    getDetail(id);
  }

  @Transactional(readOnly = true)
  public PurchaseOrder get(UUID id) {
    return getDetail(id);
  }

  @Transactional(readOnly = true)
  public PurchaseOrder getDetail(UUID id) {
    UUID organizationId = TenantContext.require();
    return orders
        .findDetailByIdAndOrganizationId(id, organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Purchase order %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<PurchaseOrder> search(PurchaseOrderFilter filter, Pageable pageable) {
    return orders.search(TenantContext.require(), filter, pageable);
  }

  private static void assertVersion(PurchaseOrder order, long expectedVersion) {
    if (expectedVersion >= 0 && order.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The purchase order was modified by another operation; reload and retry");
    }
  }
}
