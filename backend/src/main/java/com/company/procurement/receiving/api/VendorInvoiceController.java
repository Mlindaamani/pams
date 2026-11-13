package com.company.procurement.receiving.api;

import com.company.procurement.receiving.api.dto.RecordVendorInvoiceRequest;
import com.company.procurement.receiving.api.dto.VendorInvoiceResponse;
import com.company.procurement.receiving.api.mapper.VendorInvoiceMapper;
import com.company.procurement.receiving.application.RecordVendorInvoiceCommand;
import com.company.procurement.receiving.application.VendorInvoicePdfService;
import com.company.procurement.receiving.application.VendorInvoiceService;
import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceFilter;
import com.company.procurement.receiving.domain.VendorInvoiceStatus;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.ConditionalRequests;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** REST API for invoice capture and three-way matching. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Vendor invoices", description = "Invoice capture and three-way matching")
@com.company.procurement.subscription.application.RequiresFeature("invoices")
public class VendorInvoiceController {

  private static final Set<String> SORTABLE =
      Set.of("createdAt", "invoiceDate", "invoiceNumber", "totalAmount", "status");
  private static final String READ_ROLES = "@permissions.has('invoice:read')";

  private final VendorInvoiceService invoices;
  private final VendorInvoicePdfService pdf;
  private final VendorInvoiceMapper mapper;

  public VendorInvoiceController(
      VendorInvoiceService invoices, VendorInvoicePdfService pdf, VendorInvoiceMapper mapper) {
    this.invoices = invoices;
    this.pdf = pdf;
    this.mapper = mapper;
  }

  @PostMapping("/purchase-orders/{purchaseOrderId}/vendor-invoices")
  @PreAuthorize("@permissions.has('invoice:create')")
  public ResponseEntity<VendorInvoiceResponse> record(
      @PathVariable UUID purchaseOrderId, @Valid @RequestBody RecordVendorInvoiceRequest request) {
    VendorInvoice created =
        invoices.record(
            purchaseOrderId,
            new RecordVendorInvoiceCommand(
                request.invoiceNumber(),
                request.invoiceDate(),
                request.dueDate(),
                request.currency(),
                request.taxAmount(),
                request.notes(),
                request.lines().stream()
                    .map(
                        line ->
                            new RecordVendorInvoiceCommand.Line(
                                line.purchaseOrderLineId(), line.quantity(), line.unitPrice()))
                    .toList()));
    VendorInvoiceResponse body = mapper.toResponse(created);
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/vendor-invoices/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(created.getVersion()))
        .body(body);
  }

  @GetMapping("/purchase-orders/{purchaseOrderId}/vendor-invoices")
  @PreAuthorize(READ_ROLES)
  public List<VendorInvoiceResponse> forPurchaseOrder(@PathVariable UUID purchaseOrderId) {
    return invoices.forPurchaseOrder(purchaseOrderId).stream().map(mapper::toSummary).toList();
  }

  @GetMapping("/vendor-invoices")
  @PreAuthorize(READ_ROLES)
  public PageResponse<VendorInvoiceResponse> list(
      @RequestParam(required = false) VendorInvoiceStatus status,
      @RequestParam(required = false) UUID purchaseOrderId,
      @RequestParam(required = false) UUID vendorId,
      @RequestParam(required = false) String invoiceNumber,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    VendorInvoiceFilter filter =
        new VendorInvoiceFilter(status, purchaseOrderId, vendorId, invoiceNumber, dateFrom, dateTo);
    return PageResponse.from(invoices.search(filter, pageable), mapper::toSummary);
  }

  @GetMapping("/vendor-invoices/{id}")
  @PreAuthorize(READ_ROLES)
  public ResponseEntity<VendorInvoiceResponse> get(@PathVariable UUID id) {
    VendorInvoice invoice = invoices.getDetail(id);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(invoice.getVersion()))
        .body(mapper.toResponse(invoice));
  }

  @GetMapping("/vendor-invoices/{id}/pdf")
  @PreAuthorize("@permissions.has('invoice:read')")
  public org.springframework.http.ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
    VendorInvoice invoice = invoices.getDetail(id);
    byte[] content = pdf.render(invoice);
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            org.springframework.http.ContentDisposition.attachment()
                .filename(
                    (invoice.getInternalReference() == null
                            ? invoice.getInvoiceNumber()
                            : invoice.getInternalReference())
                        + ".pdf")
                .build()
                .toString())
        .body(content);
  }

  @PostMapping("/vendor-invoices/{id}/reject")
  @PreAuthorize("@permissions.has('invoice:approve')")
  public ResponseEntity<VendorInvoiceResponse> reject(
      @PathVariable UUID id,
      @Valid @RequestBody InvoiceResolutionRequest request,
      HttpServletRequest httpRequest) {
    VendorInvoice invoice =
        invoices.reject(id, ConditionalRequests.requireIfMatch(httpRequest), request.note());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(invoice.getVersion()))
        .body(mapper.toResponse(invoice));
  }

  @PostMapping("/vendor-invoices/{id}/dispute")
  @PreAuthorize("@permissions.has('invoice:approve')")
  public ResponseEntity<VendorInvoiceResponse> dispute(
      @PathVariable UUID id,
      @Valid @RequestBody InvoiceResolutionRequest request,
      HttpServletRequest httpRequest) {
    VendorInvoice invoice =
        invoices.dispute(id, ConditionalRequests.requireIfMatch(httpRequest), request.note());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(invoice.getVersion()))
        .body(mapper.toResponse(invoice));
  }

  @PostMapping("/vendor-invoices/{id}/void")
  @PreAuthorize("@permissions.has('invoice:approve')")
  public ResponseEntity<VendorInvoiceResponse> voidInvoice(
      @PathVariable UUID id,
      @Valid @RequestBody InvoiceResolutionRequest request,
      HttpServletRequest httpRequest) {
    VendorInvoice invoice =
        invoices.voidInvoice(id, ConditionalRequests.requireIfMatch(httpRequest), request.note());
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(invoice.getVersion()))
        .body(mapper.toResponse(invoice));
  }

  @PostMapping("/vendor-invoices/{id}/approve")
  @PreAuthorize("@permissions.has('invoice:approve')")
  public ResponseEntity<VendorInvoiceResponse> approve(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    VendorInvoice approved = invoices.approve(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(approved.getVersion()))
        .body(mapper.toResponse(approved));
  }

  public record InvoiceResolutionRequest(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000)
          String note) {}
}
