package com.company.procurement.receiving.services;

import com.company.procurement.audit.services.AuditEventService;
import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceStatus;
import com.company.procurement.receiving.domain.VendorPayment;
import com.company.procurement.receiving.domain.VendorPaymentAllocation;
import com.company.procurement.receiving.repositories.VendorInvoiceRepository;
import com.company.procurement.receiving.repositories.VendorPaymentAllocationRepository;
import com.company.procurement.receiving.repositories.VendorPaymentRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.numbering.DocumentNumberGenerator;
import com.company.procurement.shared.numbering.DocumentType;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.services.OutboxService;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorStatus;
import com.company.procurement.vendor.repositories.VendorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schedules, approves and settles vendor payments. */
@Service
public class VendorPaymentService {

  private final VendorPaymentRepository payments;
  private final VendorPaymentAllocationRepository allocations;
  private final VendorInvoiceRepository invoices;
  private final VendorRepository vendors;
  private final CurrentUser currentUser;
  private final AuditEventService audit;
  private final Clock clock;
  private final DocumentNumberGenerator numbers;
  private final OutboxService outbox;

  public VendorPaymentService(
      VendorPaymentRepository payments,
      VendorPaymentAllocationRepository allocations,
      VendorInvoiceRepository invoices,
      VendorRepository vendors,
      CurrentUser currentUser,
      AuditEventService audit,
      Clock clock,
      DocumentNumberGenerator numbers,
      OutboxService outbox) {
    this.payments = payments;
    this.allocations = allocations;
    this.invoices = invoices;
    this.vendors = vendors;
    this.currentUser = currentUser;
    this.audit = audit;
    this.clock = clock;
    this.numbers = numbers;
    this.outbox = outbox;
  }

  @Transactional
  public PaymentView create(CreatePaymentCommand command) {
    UUID organizationId = TenantContext.require();
    if (command.allocations() == null || command.allocations().isEmpty()) {
      throw new BusinessRuleException("At least one invoice allocation is required");
    }
    Vendor vendor =
        vendors
            .findByIdAndOrganizationId(command.vendorId(), organizationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Vendor %s not found".formatted(command.vendorId())));
    if (vendor.getStatus() != VendorStatus.ACTIVE) {
      throw new BusinessRuleException("Only active vendors can receive payments");
    }
    String currency = null;
    BigDecimal total = BigDecimal.ZERO;
    List<ValidatedAllocation> validated = new ArrayList<>();
    for (AllocationCommand allocation : command.allocations()) {
      VendorInvoice invoice =
          invoices
              .findByIdAndOrganizationId(allocation.invoiceId(), organizationId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Invoice %s not found".formatted(allocation.invoiceId())));
      if (!command.vendorId().equals(invoice.getVendorId())) {
        throw new BusinessRuleException("All invoices in a payment must belong to the same vendor");
      }
      if (invoice.getStatus() != VendorInvoiceStatus.APPROVED
          && invoice.getStatus() != VendorInvoiceStatus.PARTIALLY_PAID) {
        throw new BusinessRuleException(
            "Only approved or partially paid invoices can be scheduled for payment");
      }
      if (currency == null) {
        currency = invoice.getCurrency();
      }
      if (!currency.equals(invoice.getCurrency())) {
        throw new BusinessRuleException("All payment allocations must use the same currency");
      }
      BigDecimal outstanding = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
      BigDecimal amount = allocation.amount().setScale(4, java.math.RoundingMode.HALF_UP);
      if (amount.signum() <= 0 || amount.compareTo(outstanding) > 0) {
        throw new BusinessRuleException(
            "Payment allocation for invoice %s exceeds the outstanding amount"
                .formatted(invoice.getInvoiceNumber()));
      }
      total = total.add(amount);
      validated.add(new ValidatedAllocation(invoice.getId(), amount));
    }
    Instant now = clock.instant();
    String paymentNumber =
        numbers.next(organizationId, DocumentType.VENDOR_PAYMENT, command.paymentDate().getYear());
    VendorPayment payment =
        payments.save(
            new VendorPayment(
                organizationId,
                paymentNumber,
                command.vendorId(),
                command.paymentDate(),
                command.method(),
                command.reference(),
                currency,
                total,
                command.notes()));
    allocations.saveAll(
        validated.stream()
            .map(
                allocation ->
                    new VendorPaymentAllocation(
                        organizationId,
                        payment.getId(),
                        allocation.invoiceId(),
                        allocation.amount(),
                        now))
            .toList());
    audit.recordCurrentActor(
        "vendor_payment",
        payment.getId(),
        "vendor_payment_scheduled",
        Map.of("total", total.toPlainString(), "currency", currency));
    outbox.enqueue(
        OutboxEventTypes.VENDOR_PAYMENT_SCHEDULED,
        "vendor_payment",
        payment.getId(),
        paymentPayload(payment, "SCHEDULED"));
    return view(organizationId, payment.getId());
  }

  @Transactional
  public PaymentView approve(UUID id) {
    UUID organizationId = TenantContext.require();
    VendorPayment payment = requireForUpdate(organizationId, id);
    if (!"SCHEDULED".equals(payment.getStatus())) {
      throw new BusinessRuleException("Only scheduled payments can be approved");
    }
    payment.approve();
    payments.save(payment);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_approved", Map.of());
    outbox.enqueue(
        OutboxEventTypes.VENDOR_PAYMENT_APPROVED,
        "vendor_payment",
        id,
        paymentPayload(payment, "APPROVED"));
    return view(organizationId, id);
  }

  @Transactional
  public PaymentView markPaid(UUID id) {
    UUID organizationId = TenantContext.require();
    VendorPayment payment = requireForUpdate(organizationId, id);
    if (!"APPROVED".equals(payment.getStatus())) {
      throw new BusinessRuleException("Only approved payments can be marked paid");
    }
    List<VendorPaymentAllocation> paymentAllocations = allocations.findByPaymentId(id);
    paymentAllocations.stream()
        .sorted(Comparator.comparing(VendorPaymentAllocation::getInvoiceId))
        .forEach(
            allocation -> {
              VendorInvoice invoice =
                  invoices
                      .findForUpdate(allocation.getInvoiceId(), organizationId)
                      .orElseThrow(
                          () ->
                              new ResourceNotFoundException(
                                  "Invoice %s not found".formatted(allocation.getInvoiceId())));
              invoice.applyPayment(allocation.getAmount());
              invoices.save(invoice);
            });
    Instant now = clock.instant();
    payment.markPaid(now);
    payments.save(payment);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_paid", Map.of());
    outbox.enqueue(
        OutboxEventTypes.VENDOR_PAYMENT_PAID,
        "vendor_payment",
        id,
        paymentPayload(payment, "PAID"));
    for (VendorPaymentAllocation allocation : paymentAllocations) {
      VendorInvoice invoice =
          invoices
              .findByIdAndOrganizationId(allocation.getInvoiceId(), organizationId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Invoice %s not found".formatted(allocation.getInvoiceId())));
      if (invoice.getStatus() == VendorInvoiceStatus.PAID) {
        outbox.enqueue(
            OutboxEventTypes.VENDOR_INVOICE_PAID,
            "vendor_invoice",
            invoice.getId(),
            Map.of(
                "invoiceId", invoice.getId().toString(),
                "invoiceNumber", invoice.getInvoiceNumber(),
                "purchaseOrderId", invoice.getPurchaseOrderId().toString(),
                "poNumber", invoice.getPoNumber(),
                "status", "PAID",
                "total", invoice.getTotalAmount().toString(),
                "currency", invoice.getCurrency()));
      }
    }
    return view(organizationId, id);
  }

  @Transactional
  public PaymentView cancel(UUID id) {
    UUID organizationId = TenantContext.require();
    VendorPayment payment = requireForUpdate(organizationId, id);
    if ("PAID".equals(payment.getStatus()) || "CANCELED".equals(payment.getStatus())) {
      throw new BusinessRuleException("This payment can no longer be canceled");
    }
    payment.cancel();
    payments.save(payment);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_canceled", Map.of());
    outbox.enqueue(
        OutboxEventTypes.VENDOR_PAYMENT_CANCELLED,
        "vendor_payment",
        id,
        paymentPayload(payment, "CANCELED"));
    return view(organizationId, id);
  }

  private static Map<String, Object> paymentPayload(VendorPayment payment, String status) {
    return Map.of(
        "paymentId", payment.getId().toString(),
        "paymentNumber", payment.getPaymentNumber(),
        "vendorId", payment.getVendorId().toString(),
        "status", status,
        "total", payment.getTotalAmount().toPlainString(),
        "currency", payment.getCurrency());
  }

  @Transactional(readOnly = true)
  public List<PaymentView> list() {
    UUID organizationId = TenantContext.require();
    List<VendorPayment> paymentRows = payments.findByOrganizationId(organizationId);
    if (paymentRows.isEmpty()) return List.of();
    List<UUID> paymentIds = paymentRows.stream().map(VendorPayment::getId).toList();
    Map<UUID, List<VendorPaymentAllocation>> allocationsByPayment =
        allocations.findByPaymentIds(paymentIds).stream()
            .collect(Collectors.groupingBy(VendorPaymentAllocation::getPaymentId));
    Map<UUID, String> invoiceNumbers =
        invoiceNumbers(
            allocationsByPayment.values().stream()
                .flatMap(List::stream)
                .map(VendorPaymentAllocation::getInvoiceId)
                .toList());
    return paymentRows.stream()
        .map(
            payment ->
                view(
                    payment,
                    allocationsByPayment.getOrDefault(payment.getId(), List.of()),
                    invoiceNumbers))
        .toList();
  }

  @Transactional(readOnly = true)
  public PaymentView get(UUID id) {
    return view(TenantContext.require(), id);
  }

  @Transactional(readOnly = true)
  public AgingReport aging() {
    UUID organizationId = TenantContext.require();
    List<AgingRow> rows =
        invoices.aging(organizationId).stream()
            .map(
                row ->
                    new AgingRow(
                        row.vendorId(),
                        row.vendorName(),
                        row.currency(),
                        row.currentAmount(),
                        row.days1To30(),
                        row.days31To60(),
                        row.days61To90(),
                        row.daysOver90(),
                        row.totalOutstanding(),
                        row.invoiceCount()))
            .toList();
    return new AgingReport(clock.instant(), rows);
  }

  private VendorPayment requireForUpdate(UUID organizationId, UUID id) {
    return payments
        .findForUpdate(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment %s not found".formatted(id)));
  }

  private PaymentView view(UUID organizationId, UUID paymentId) {
    VendorPayment payment =
        payments
            .findByIdAndOrganizationId(paymentId, organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Payment %s not found".formatted(paymentId)));
    List<VendorPaymentAllocation> paymentAllocations = allocations.findByPaymentId(paymentId);
    Map<UUID, String> invoiceNumbers =
        invoiceNumbers(
            paymentAllocations.stream().map(VendorPaymentAllocation::getInvoiceId).toList());
    return view(payment, paymentAllocations, invoiceNumbers);
  }

  private PaymentView view(
      VendorPayment payment,
      List<VendorPaymentAllocation> paymentAllocations,
      Map<UUID, String> invoiceNumbers) {
    List<AllocationView> allocationViews =
        paymentAllocations.stream()
            .map(
                allocation ->
                    new AllocationView(
                        allocation.getInvoiceId(),
                        invoiceNumbers.getOrDefault(allocation.getInvoiceId(), "—"),
                        allocation.getAmount()))
            .sorted(Comparator.comparing(AllocationView::invoiceNumber))
            .toList();
    return new PaymentView(
        payment.getId(),
        payment.getPaymentNumber(),
        payment.getVendorId(),
        payment.getStatus(),
        payment.getPaymentDate(),
        payment.getMethod(),
        payment.getReference(),
        payment.getCurrency(),
        payment.getTotalAmount(),
        payment.getNotes(),
        payment.getPaidAt(),
        payment.getVersion(),
        allocationViews);
  }

  private Map<UUID, String> invoiceNumbers(List<UUID> invoiceIds) {
    if (invoiceIds.isEmpty()) return Map.of();
    return invoices.findAllByIds(invoiceIds).stream()
        .collect(Collectors.toMap(VendorInvoice::getId, VendorInvoice::getInvoiceNumber));
  }

  public record CreatePaymentCommand(
      UUID vendorId,
      LocalDate paymentDate,
      String method,
      String reference,
      String notes,
      List<AllocationCommand> allocations) {}

  public record AllocationCommand(UUID invoiceId, BigDecimal amount) {}

  public record PaymentView(
      UUID id,
      String paymentNumber,
      UUID vendorId,
      String status,
      LocalDate paymentDate,
      String method,
      String reference,
      String currency,
      BigDecimal totalAmount,
      String notes,
      Instant paidAt,
      long version,
      List<AllocationView> allocations) {}

  public record AllocationView(UUID invoiceId, String invoiceNumber, BigDecimal amount) {}

  public record AgingReport(Instant generatedAt, List<AgingRow> rows) {}

  public record AgingRow(
      UUID vendorId,
      String vendorName,
      String currency,
      BigDecimal current,
      BigDecimal days1To30,
      BigDecimal days31To60,
      BigDecimal days61To90,
      BigDecimal daysOver90,
      BigDecimal totalOutstanding,
      long invoiceCount) {}

  private record ValidatedAllocation(UUID invoiceId, BigDecimal amount) {}
}
