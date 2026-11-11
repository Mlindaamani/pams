package com.company.procurement.receiving.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.numbering.DocumentNumberGenerator;
import com.company.procurement.shared.numbering.DocumentType;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorPaymentService {

  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final AuditEventService audit;
  private final Clock clock;
  private final DocumentNumberGenerator numbers;

  public VendorPaymentService(
      JdbcTemplate jdbc,
      CurrentUser currentUser,
      AuditEventService audit,
      Clock clock,
      DocumentNumberGenerator numbers) {
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.audit = audit;
    this.clock = clock;
    this.numbers = numbers;
  }

  @Transactional
  public PaymentView create(CreatePaymentCommand command) {
    UUID organizationId = TenantContext.require();
    if (command.allocations() == null || command.allocations().isEmpty()) {
      throw new BusinessRuleException("At least one invoice allocation is required");
    }
    Map<String, Object> vendor =
        jdbc
            .queryForList(
                "select id, status from vendor where organization_id = ? and id = ? and deleted_at is null",
                organizationId,
                command.vendorId())
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Vendor %s not found".formatted(command.vendorId())));
    if (!"ACTIVE".equals(vendor.get("status"))) {
      throw new BusinessRuleException("Only active vendors can receive payments");
    }
    String currency = null;
    BigDecimal total = BigDecimal.ZERO;
    List<AllocationValidation> validated = new ArrayList<>();
    for (AllocationCommand allocation : command.allocations()) {
      Map<String, Object> invoice =
          jdbc
              .queryForList(
                  "select id, vendor_id, invoice_number, status, currency, total_amount, paid_amount from vendor_invoice where organization_id = ? and id = ?",
                  organizationId,
                  allocation.invoiceId())
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Invoice %s not found".formatted(allocation.invoiceId())));
      if (!command.vendorId().equals(invoice.get("vendor_id"))) {
        throw new BusinessRuleException("All invoices in a payment must belong to the same vendor");
      }
      String status = (String) invoice.get("status");
      if (!"APPROVED".equals(status) && !"PARTIALLY_PAID".equals(status)) {
        throw new BusinessRuleException(
            "Only approved or partially paid invoices can be scheduled for payment");
      }
      String invoiceCurrency = (String) invoice.get("currency");
      if (currency == null) currency = invoiceCurrency;
      if (!currency.equals(invoiceCurrency)) {
        throw new BusinessRuleException("All payment allocations must use the same currency");
      }
      BigDecimal outstanding =
          ((BigDecimal) invoice.get("total_amount"))
              .subtract((BigDecimal) invoice.get("paid_amount"));
      BigDecimal amount = allocation.amount().setScale(4, java.math.RoundingMode.HALF_UP);
      if (amount.signum() <= 0 || amount.compareTo(outstanding) > 0) {
        throw new BusinessRuleException(
            "Payment allocation for invoice %s exceeds the outstanding amount"
                .formatted(invoice.get("invoice_number")));
      }
      total = total.add(amount);
      validated.add(
          new AllocationValidation(
              allocation.invoiceId(), (String) invoice.get("invoice_number"), amount));
    }
    UUID paymentId = UUID.randomUUID();
    Instant now = clock.instant();
    String paymentNumber =
        numbers.next(organizationId, DocumentType.VENDOR_PAYMENT, command.paymentDate().getYear());
    jdbc.update(
        """
        insert into vendor_payment
            (id, organization_id, payment_number, vendor_id, status, payment_date, method, reference,
             currency, total_amount, notes, created_at, created_by, updated_at, updated_by)
        values (?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        paymentId,
        organizationId,
        paymentNumber,
        command.vendorId(),
        java.sql.Date.valueOf(command.paymentDate()),
        command.method(),
        command.reference(),
        currency,
        total,
        command.notes(),
        java.sql.Timestamp.from(now),
        currentUser.username(),
        java.sql.Timestamp.from(now),
        currentUser.username());
    for (AllocationValidation allocation : validated) {
      jdbc.update(
          """
          insert into vendor_payment_allocation
              (id, organization_id, payment_id, invoice_id, amount, created_at)
          values (?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          organizationId,
          paymentId,
          allocation.invoiceId(),
          allocation.amount(),
          java.sql.Timestamp.from(now));
    }
    audit.recordCurrentActor(
        "vendor_payment",
        paymentId,
        "vendor_payment_scheduled",
        Map.of("total", total.toPlainString(), "currency", currency));
    return view(organizationId, paymentId);
  }

  @Transactional
  public PaymentView approve(UUID id) {
    UUID organizationId = TenantContext.require();
    PaymentRow payment = payment(organizationId, id);
    if (!"SCHEDULED".equals(payment.status())) {
      throw new BusinessRuleException("Only scheduled payments can be approved");
    }
    jdbc.update(
        "update vendor_payment set status = 'APPROVED', updated_at = now(), updated_by = ?, version = version + 1 where organization_id = ? and id = ?",
        currentUser.username(),
        organizationId,
        id);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_approved", Map.of());
    return view(organizationId, id);
  }

  @Transactional
  public PaymentView markPaid(UUID id) {
    UUID organizationId = TenantContext.require();
    PaymentRow payment = payment(organizationId, id);
    if (!"APPROVED".equals(payment.status())) {
      throw new BusinessRuleException("Only approved payments can be marked paid");
    }
    List<Map<String, Object>> allocations =
        jdbc.queryForList(
            "select invoice_id, amount from vendor_payment_allocation where organization_id = ? and payment_id = ?",
            organizationId,
            id);
    for (Map<String, Object> allocation : allocations) {
      int updated =
          jdbc.update(
              """
              update vendor_invoice
                 set paid_amount = paid_amount + ?,
                     status = case when paid_amount + ? >= total_amount then 'PAID'
                                   else 'PARTIALLY_PAID' end,
                     updated_at = now(),
                     version = version + 1
               where organization_id = ? and id = ?
                 and status in ('APPROVED', 'PARTIALLY_PAID')
                 and paid_amount + ? <= total_amount
              """,
              allocation.get("amount"),
              allocation.get("amount"),
              organizationId,
              allocation.get("invoice_id"),
              allocation.get("amount"));
      if (updated != 1) {
        throw new ConflictException("Invoice payment allocation could not be applied");
      }
    }
    jdbc.update(
        "update vendor_payment set status = 'PAID', paid_at = now(), updated_at = now(), updated_by = ?, version = version + 1 where organization_id = ? and id = ?",
        currentUser.username(),
        organizationId,
        id);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_paid", Map.of());
    return view(organizationId, id);
  }

  @Transactional
  public PaymentView cancel(UUID id) {
    UUID organizationId = TenantContext.require();
    PaymentRow payment = payment(organizationId, id);
    if ("PAID".equals(payment.status()) || "CANCELED".equals(payment.status())) {
      throw new BusinessRuleException("This payment can no longer be canceled");
    }
    jdbc.update(
        "update vendor_payment set status = 'CANCELED', updated_at = now(), updated_by = ?, version = version + 1 where organization_id = ? and id = ?",
        currentUser.username(),
        organizationId,
        id);
    audit.recordCurrentActor("vendor_payment", id, "vendor_payment_canceled", Map.of());
    return view(organizationId, id);
  }

  @Transactional(readOnly = true)
  public List<PaymentView> list() {
    UUID organizationId = TenantContext.require();
    return jdbc
        .queryForList(
            "select id from vendor_payment where organization_id = ? order by payment_date desc, created_at desc",
            organizationId)
        .stream()
        .map(row -> view(organizationId, (UUID) row.get("id")))
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
        jdbc.query(
            """
            select v.id vendor_id, v.name vendor_name, i.currency,
                   sum(case when coalesce(i.due_date, i.invoice_date) >= current_date
                            then i.total_amount - i.paid_amount else 0 end) as current_amount,
                   sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                  between 1 and 30
                            then i.total_amount - i.paid_amount else 0 end) as days_1_30,
                   sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                  between 31 and 60
                            then i.total_amount - i.paid_amount else 0 end) as days_31_60,
                   sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                  between 61 and 90
                            then i.total_amount - i.paid_amount else 0 end) as days_61_90,
                   sum(case when current_date - coalesce(i.due_date, i.invoice_date) > 90
                            then i.total_amount - i.paid_amount else 0 end) as days_over_90,
                   sum(i.total_amount - i.paid_amount) as total_outstanding,
                   count(*) as invoice_count
              from vendor_invoice i
              join vendor v on v.id = i.vendor_id
             where i.organization_id = ?
               and i.status in ('APPROVED', 'PARTIALLY_PAID')
               and i.total_amount - i.paid_amount > 0
             group by v.id, v.name, i.currency
             order by v.name, i.currency
            """,
            (rs, row) ->
                new AgingRow(
                    rs.getObject("vendor_id", UUID.class),
                    rs.getString("vendor_name"),
                    rs.getString("currency"),
                    rs.getBigDecimal("current_amount"),
                    rs.getBigDecimal("days_1_30"),
                    rs.getBigDecimal("days_31_60"),
                    rs.getBigDecimal("days_61_90"),
                    rs.getBigDecimal("days_over_90"),
                    rs.getBigDecimal("total_outstanding"),
                    rs.getLong("invoice_count")),
            organizationId);
    return new AgingReport(clock.instant(), rows);
  }

  private PaymentView view(UUID organizationId, UUID paymentId) {
    PaymentRow payment = payment(organizationId, paymentId);
    List<AllocationView> allocations =
        jdbc.query(
            """
            select a.invoice_id, i.invoice_number, a.amount
              from vendor_payment_allocation a
              join vendor_invoice i on i.id = a.invoice_id
             where a.organization_id = ? and a.payment_id = ?
             order by i.invoice_number
            """,
            (rs, row) ->
                new AllocationView(
                    rs.getObject("invoice_id", UUID.class),
                    rs.getString("invoice_number"),
                    rs.getBigDecimal("amount")),
            organizationId,
            paymentId);
    return new PaymentView(
        payment.id(),
        payment.paymentNumber(),
        payment.vendorId(),
        payment.status(),
        payment.paymentDate(),
        payment.method(),
        payment.reference(),
        payment.currency(),
        payment.totalAmount(),
        payment.notes(),
        payment.paidAt(),
        payment.version(),
        allocations);
  }

  private PaymentRow payment(UUID organizationId, UUID id) {
    return jdbc
        .query(
            """
            select id, payment_number, vendor_id, status, payment_date, method, reference, currency,
                   total_amount, notes, paid_at, version
              from vendor_payment where organization_id = ? and id = ?
            """,
            (rs, row) ->
                new PaymentRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("payment_number"),
                    rs.getObject("vendor_id", UUID.class),
                    rs.getString("status"),
                    rs.getDate("payment_date").toLocalDate(),
                    rs.getString("method"),
                    rs.getString("reference"),
                    rs.getString("currency"),
                    rs.getBigDecimal("total_amount"),
                    rs.getString("notes"),
                    rs.getTimestamp("paid_at") == null
                        ? null
                        : rs.getTimestamp("paid_at").toInstant(),
                    rs.getLong("version")),
            organizationId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Payment %s not found".formatted(id)));
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

  private record AllocationValidation(UUID invoiceId, String invoiceNumber, BigDecimal amount) {}

  private record PaymentRow(
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
      long version) {}
}
