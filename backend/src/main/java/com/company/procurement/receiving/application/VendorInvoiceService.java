package com.company.procurement.receiving.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.organization.application.OrganizationService;
import com.company.procurement.purchaseorder.application.InvoiceMatchData;
import com.company.procurement.purchaseorder.application.PurchaseOrderService;
import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceFilter;
import com.company.procurement.receiving.domain.VendorInvoiceLineDraft;
import com.company.procurement.receiving.domain.VendorInvoiceRepository;
import com.company.procurement.receiving.domain.VendorInvoiceStatus;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.outbox.OutboxService;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorInvoiceService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final VendorInvoiceRepository invoices;
  private final PurchaseOrderService orders;
  private final OrganizationService organizations;
  private final AuditEventService audit;
  private final CurrentUser currentUser;
  private final OutboxService outbox;
  private final InvoiceReferenceGenerator references;
  private final Clock clock;

  public VendorInvoiceService(
      VendorInvoiceRepository invoices,
      PurchaseOrderService orders,
      OrganizationService organizations,
      AuditEventService audit,
      CurrentUser currentUser,
      OutboxService outbox,
      InvoiceReferenceGenerator references,
      Clock clock) {
    this.invoices = invoices;
    this.orders = orders;
    this.organizations = organizations;
    this.audit = audit;
    this.currentUser = currentUser;
    this.outbox = outbox;
    this.references = references;
    this.clock = clock;
  }

  @Transactional
  public VendorInvoice record(UUID purchaseOrderId, RecordVendorInvoiceCommand command) {
    UUID organizationId = TenantContext.require();
    String invoiceNumber = command.invoiceNumber().trim();
    String currency = command.currency().trim().toUpperCase(Locale.ROOT);
    InvoiceMatchData order = orders.invoiceMatchDataForUpdate(purchaseOrderId);
    if (invoices.existsActiveByVendorAndNumber(organizationId, order.vendorId(), invoiceNumber)) {
      throw new ConflictException(
          "Invoice number '%s' already exists for this vendor".formatted(invoiceNumber));
    }
    if (!order.currency().equals(currency)) {
      throw new BusinessRuleException(
          "Invoice currency %s does not match purchase order currency %s"
              .formatted(currency, order.currency()));
    }

    BigDecimal tolerance = organizations.currentInvoiceTolerancePercent();
    Map<UUID, BigDecimal> alreadyInvoiced = new HashMap<>();
    invoices
        .invoicedQuantities(organizationId, purchaseOrderId)
        .forEach(item -> alreadyInvoiced.put(item.purchaseOrderLineId(), item.quantity()));

    Map<UUID, InvoiceMatchData.Line> orderLines = new LinkedHashMap<>();
    order.lines().forEach(line -> orderLines.put(line.id(), line));

    Map<UUID, RecordVendorInvoiceCommand.Line> requested = new LinkedHashMap<>();
    for (RecordVendorInvoiceCommand.Line line : command.lines()) {
      if (requested.putIfAbsent(line.purchaseOrderLineId(), line) != null) {
        throw new BusinessRuleException("Each purchase order line may appear only once");
      }
    }

    List<VendorInvoiceLineDraft> drafts = new ArrayList<>();
    BigDecimal subtotal = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    boolean allMatched = true;
    int mismatchCount = 0;
    for (RecordVendorInvoiceCommand.Line line : requested.values()) {
      InvoiceMatchData.Line orderLine = orderLines.get(line.purchaseOrderLineId());
      if (orderLine == null) {
        throw new BusinessRuleException(
            "Purchase order line %s does not belong to %s"
                .formatted(line.purchaseOrderLineId(), order.poNumber()));
      }
      BigDecimal lineTotal =
          line.quantity().multiply(line.unitPrice()).setScale(4, RoundingMode.HALF_UP);
      subtotal = subtotal.add(lineTotal);
      BigDecimal cumulativeInvoiced =
          alreadyInvoiced
              .getOrDefault(line.purchaseOrderLineId(), BigDecimal.ZERO)
              .add(line.quantity());
      boolean quantityMatched = cumulativeInvoiced.compareTo(orderLine.receivedQuantity()) <= 0;
      BigDecimal variance = variancePercent(orderLine.unitPrice(), line.unitPrice());
      boolean priceMatched = variance.compareTo(tolerance) <= 0;
      boolean matched = quantityMatched && priceMatched;
      if (!matched) {
        allMatched = false;
        mismatchCount++;
      }
      List<String> messages = new ArrayList<>();
      if (!quantityMatched) {
        messages.add(
            "Invoiced quantity %s exceeds received quantity %s"
                .formatted(
                    cumulativeInvoiced.stripTrailingZeros().toPlainString(),
                    orderLine.receivedQuantity().stripTrailingZeros().toPlainString()));
      }
      if (!priceMatched) {
        messages.add(
            "Price variance %s%% exceeds tolerance %s%%"
                .formatted(
                    variance.stripTrailingZeros().toPlainString(),
                    tolerance.stripTrailingZeros().toPlainString()));
      }
      drafts.add(
          new VendorInvoiceLineDraft(
              orderLine.id(),
              orderLine.description(),
              orderLine.unit(),
              line.quantity(),
              line.unitPrice(),
              lineTotal,
              matched,
              variance,
              messages.isEmpty() ? null : String.join("; ", messages)));
    }

    BigDecimal tax =
        (command.taxAmount() == null ? BigDecimal.ZERO : command.taxAmount())
            .setScale(4, RoundingMode.HALF_UP);
    BigDecimal total = subtotal.add(tax).setScale(4, RoundingMode.HALF_UP);
    VendorInvoiceStatus status =
        allMatched ? VendorInvoiceStatus.MATCHED : VendorInvoiceStatus.MISMATCHED;
    String internalReference =
        references.nextNumber(organizationId, command.invoiceDate().getYear());
    VendorInvoice invoice =
        new VendorInvoice(
            organizationId,
            order.purchaseOrderId(),
            order.poNumber(),
            order.vendorId(),
            invoiceNumber,
            internalReference,
            command.invoiceDate(),
            command.dueDate(),
            currency,
            subtotal,
            tax,
            total,
            status,
            tolerance,
            command.notes(),
            drafts);
    VendorInvoice saved = invoices.save(invoice);
    outbox.enqueue(
        OutboxEventTypes.VENDOR_INVOICE_RECORDED,
        "vendor_invoice",
        saved.getId(),
        Map.of(
            "invoiceId", saved.getId().toString(),
            "invoiceNumber", saved.getInvoiceNumber(),
            "purchaseOrderId", purchaseOrderId.toString(),
            "poNumber", saved.getPoNumber(),
            "status", saved.getStatus().name(),
            "total", saved.getTotalAmount().toPlainString(),
            "currency", saved.getCurrency()));
    audit.recordCurrentActor(
        "vendor_invoice",
        saved.getId(),
        "vendor_invoice_recorded",
        Map.of(
            "invoiceNumber", saved.getInvoiceNumber(),
            "purchaseOrderId", purchaseOrderId.toString(),
            "status", saved.getStatus().name(),
            "mismatchLines", mismatchCount));
    return saved;
  }

  @Transactional
  public VendorInvoice approve(UUID id, long expectedVersion) {
    VendorInvoice invoice = getDetail(id);
    if (expectedVersion >= 0 && invoice.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The invoice was modified by another operation; reload and retry");
    }
    invoice.approve(clock.instant(), currentUser.subject());
    VendorInvoice saved = invoices.save(invoice);
    audit.recordCurrentActor(
        "vendor_invoice",
        saved.getId(),
        "vendor_invoice_approved",
        Map.of("invoiceNumber", saved.getInvoiceNumber()));
    outbox.enqueue(
        OutboxEventTypes.VENDOR_INVOICE_APPROVED,
        "vendor_invoice",
        saved.getId(),
        invoicePayload(saved));
    return saved;
  }

  @Transactional
  public VendorInvoice reject(UUID id, long expectedVersion, String note) {
    VendorInvoice invoice = getDetail(id);
    assertVersion(invoice, expectedVersion);
    invoice.reject(clock.instant(), currentUser.subject(), note);
    VendorInvoice saved = invoices.save(invoice);
    audit.recordCurrentActor(
        "vendor_invoice",
        saved.getId(),
        "vendor_invoice_rejected",
        Map.of("invoiceNumber", saved.getInvoiceNumber(), "reason", note));
    outbox.enqueue(
        OutboxEventTypes.VENDOR_INVOICE_REJECTED,
        "vendor_invoice",
        saved.getId(),
        invoicePayload(saved));
    return saved;
  }

  @Transactional
  public VendorInvoice dispute(UUID id, long expectedVersion, String note) {
    VendorInvoice invoice = getDetail(id);
    assertVersion(invoice, expectedVersion);
    invoice.dispute(clock.instant(), currentUser.subject(), note);
    VendorInvoice saved = invoices.save(invoice);
    audit.recordCurrentActor(
        "vendor_invoice",
        saved.getId(),
        "vendor_invoice_disputed",
        Map.of("invoiceNumber", saved.getInvoiceNumber(), "reason", note));
    outbox.enqueue(
        OutboxEventTypes.VENDOR_INVOICE_DISPUTED,
        "vendor_invoice",
        saved.getId(),
        invoicePayload(saved));
    return saved;
  }

  @Transactional
  public VendorInvoice voidInvoice(UUID id, long expectedVersion, String note) {
    VendorInvoice invoice = getDetail(id);
    assertVersion(invoice, expectedVersion);
    invoice.voidInvoice(clock.instant(), currentUser.subject(), note);
    VendorInvoice saved = invoices.save(invoice);
    audit.recordCurrentActor(
        "vendor_invoice",
        saved.getId(),
        "vendor_invoice_voided",
        Map.of("invoiceNumber", saved.getInvoiceNumber(), "reason", note));
    return saved;
  }

  private static Map<String, Object> invoicePayload(VendorInvoice invoice) {
    return Map.of(
        "invoiceId", invoice.getId().toString(),
        "invoiceNumber", invoice.getInvoiceNumber(),
        "purchaseOrderId", invoice.getPurchaseOrderId().toString(),
        "poNumber", invoice.getPoNumber(),
        "status", invoice.getStatus().name(),
        "total", invoice.getTotalAmount().toPlainString(),
        "currency", invoice.getCurrency());
  }

  private static void assertVersion(VendorInvoice invoice, long expectedVersion) {
    if (expectedVersion >= 0 && invoice.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The invoice was modified by another operation; reload and retry");
    }
  }

  @Transactional(readOnly = true)
  public VendorInvoice getDetail(UUID id) {
    UUID organizationId = TenantContext.require();
    return invoices
        .findDetailByIdAndOrganizationId(id, organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Vendor invoice %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public List<VendorInvoice> forPurchaseOrder(UUID purchaseOrderId) {
    UUID organizationId = TenantContext.require();
    orders.requireVisible(purchaseOrderId);
    return invoices
        .search(
            organizationId,
            new VendorInvoiceFilter(null, purchaseOrderId, null, null, null, null),
            Pageable.unpaged())
        .getContent();
  }

  @Transactional(readOnly = true)
  public Page<VendorInvoice> search(VendorInvoiceFilter filter, Pageable pageable) {
    return invoices.search(TenantContext.require(), filter, pageable);
  }

  private static BigDecimal variancePercent(BigDecimal orderedPrice, BigDecimal invoicePrice) {
    if (orderedPrice.signum() == 0) {
      return invoicePrice.signum() == 0
          ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
          : HUNDRED.setScale(4, RoundingMode.HALF_UP);
    }
    return invoicePrice
        .subtract(orderedPrice)
        .abs()
        .multiply(HUNDRED)
        .divide(orderedPrice, 4, RoundingMode.HALF_UP);
  }
}
