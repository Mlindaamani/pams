package com.company.procurement.receiving.api;

import com.company.procurement.receiving.application.VendorPaymentService;
import com.company.procurement.receiving.application.VendorPaymentService.AllocationCommand;
import com.company.procurement.receiving.application.VendorPaymentService.CreatePaymentCommand;
import com.company.procurement.receiving.application.VendorPaymentService.PaymentView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/vendor-payments")
@Tag(name = "Vendor payments", description = "AP payment scheduling and recording")
@com.company.procurement.subscription.application.RequiresFeature("invoices")
public class VendorPaymentController {

  private final VendorPaymentService payments;

  public VendorPaymentController(VendorPaymentService payments) {
    this.payments = payments;
  }

  @GetMapping
  @PreAuthorize("@permissions.has('payment:read')")
  public List<PaymentView> list() {
    return payments.list();
  }

  @GetMapping("/aging")
  @PreAuthorize("@permissions.has('payment:read')")
  public VendorPaymentService.AgingReport aging() {
    return payments.aging();
  }

  @GetMapping("/{id}")
  @PreAuthorize("@permissions.has('payment:read')")
  public PaymentView get(@PathVariable UUID id) {
    return payments.get(id);
  }

  @PostMapping
  @PreAuthorize("@permissions.has('payment:manage')")
  public ResponseEntity<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request) {
    PaymentView created =
        payments.create(
            new CreatePaymentCommand(
                request.vendorId(),
                request.paymentDate(),
                request.method(),
                request.reference(),
                request.notes(),
                request.allocations().stream()
                    .map(
                        allocation ->
                            new AllocationCommand(allocation.invoiceId(), allocation.amount()))
                    .toList()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("@permissions.has('payment:manage')")
  public PaymentView approve(@PathVariable UUID id) {
    return payments.approve(id);
  }

  @PostMapping("/{id}/pay")
  @PreAuthorize("@permissions.has('payment:manage')")
  public PaymentView pay(@PathVariable UUID id) {
    return payments.markPaid(id);
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("@permissions.has('payment:manage')")
  public PaymentView cancel(@PathVariable UUID id) {
    return payments.cancel(id);
  }

  public record CreatePaymentRequest(
      @NotNull UUID vendorId,
      @NotNull LocalDate paymentDate,
      @NotBlank @Size(max = 30) String method,
      @Size(max = 200) String reference,
      @Size(max = 1000) String notes,
      @NotEmpty @Valid List<AllocationRequest> allocations) {}

  public record AllocationRequest(
      @NotNull UUID invoiceId, @NotNull @DecimalMin(value = "0.0001") BigDecimal amount) {}
}
