package com.company.procurement.purchaseorder.api;

import com.company.procurement.purchaseorder.api.dto.CreatePurchaseOrderRequest;
import com.company.procurement.purchaseorder.api.dto.PurchaseOrderResponse;
import com.company.procurement.purchaseorder.api.dto.UpdatePurchaseOrderRequest;
import com.company.procurement.purchaseorder.api.mapper.PurchaseOrderMapper;
import com.company.procurement.purchaseorder.application.CreatePurchaseOrderCommand;
import com.company.procurement.purchaseorder.application.PurchaseOrderActionService;
import com.company.procurement.purchaseorder.application.PurchaseOrderApprovalService;
import com.company.procurement.purchaseorder.application.PurchaseOrderPdfService;
import com.company.procurement.purchaseorder.application.PurchaseOrderService;
import com.company.procurement.purchaseorder.application.UpdatePurchaseOrderCommand;
import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderFilter;
import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.ConditionalRequests;
import com.company.procurement.shared.web.PageRequests;
import com.company.procurement.vendor.application.VendorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Purchase orders", description = "Conversion of approved requests, issuance and PDF")
@com.company.procurement.subscription.application.RequiresFeature("purchase_orders")
public class PurchaseOrderController {

  private static final Set<String> SORTABLE =
      Set.of("createdAt", "totalAmount", "poNumber", "status", "issuedAt");

  private final PurchaseOrderService orders;
  private final PurchaseOrderActionService actions;
  private final PurchaseOrderApprovalService approvals;
  private final PurchaseOrderPdfService pdf;
  private final VendorService vendors;
  private final PurchaseOrderMapper mapper;

  public PurchaseOrderController(
      PurchaseOrderService orders,
      PurchaseOrderActionService actions,
      PurchaseOrderApprovalService approvals,
      PurchaseOrderPdfService pdf,
      VendorService vendors,
      PurchaseOrderMapper mapper) {
    this.orders = orders;
    this.actions = actions;
    this.approvals = approvals;
    this.pdf = pdf;
    this.vendors = vendors;
    this.mapper = mapper;
  }

  @PostMapping("/purchase-requests/{requestId}/purchase-orders")
  @PreAuthorize("@permissions.has('purchase_order:create')")
  public ResponseEntity<PurchaseOrderResponse> create(
      @PathVariable UUID requestId, @Valid @RequestBody CreatePurchaseOrderRequest request) {
    PurchaseOrder created =
        orders.create(
            requestId,
            new CreatePurchaseOrderCommand(
                request.vendorId(), request.paymentTerms(), request.deliveryDate()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/purchase-orders/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(created.getVersion()))
        .body(mapper.toResponse(created));
  }

  @GetMapping("/purchase-orders/{id}")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public ResponseEntity<PurchaseOrderResponse> get(@PathVariable UUID id) {
    PurchaseOrder order = orders.get(id);
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(order.getVersion()))
        .body(mapper.toResponse(order));
  }

  @GetMapping("/purchase-orders/{id}/actions")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public PurchaseOrderActionService.PurchaseOrderActionsResponse actions(@PathVariable UUID id) {
    return actions.forOrder(id);
  }

  @GetMapping("/purchase-orders")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public PageResponse<PurchaseOrderResponse> list(
      @RequestParam(required = false) PurchaseOrderStatus status,
      @RequestParam(required = false) UUID vendorId,
      @RequestParam(required = false) UUID requestId,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    PurchaseOrderFilter filter =
        new PurchaseOrderFilter(status, vendorId, requestId, createdFrom, createdTo, q);
    return PageResponse.from(orders.search(filter, pageable), mapper::toSummary);
  }

  @PutMapping("/purchase-orders/{id}")
  @PreAuthorize("@permissions.has('purchase_order:update')")
  public ResponseEntity<PurchaseOrderResponse> updateDraft(
      @PathVariable UUID id,
      @Valid @RequestBody UpdatePurchaseOrderRequest request,
      HttpServletRequest httpRequest) {
    PurchaseOrder updated =
        orders.updateDraft(
            id,
            ConditionalRequests.requireIfMatch(httpRequest),
            new UpdatePurchaseOrderCommand(request.paymentTerms(), request.deliveryDate()));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(updated.getVersion()))
        .body(mapper.toResponse(updated));
  }

  @PostMapping("/purchase-orders/{id}/issue")
  @PreAuthorize("@permissions.has('purchase_order:issue')")
  public ResponseEntity<PurchaseOrderResponse> issue(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    PurchaseOrder issued = orders.issue(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(issued.getVersion()))
        .body(mapper.toResponse(issued));
  }

  @PostMapping("/purchase-orders/{id}/cancel")
  @PreAuthorize("@permissions.has('purchase_order:cancel')")
  public ResponseEntity<PurchaseOrderResponse> cancel(
      @PathVariable UUID id, HttpServletRequest httpRequest) {
    PurchaseOrder cancelled = orders.cancel(id, ConditionalRequests.requireIfMatch(httpRequest));
    return ResponseEntity.ok()
        .eTag(ConditionalRequests.etag(cancelled.getVersion()))
        .body(mapper.toResponse(cancelled));
  }

  @GetMapping("/purchase-orders/{id}/approval")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public PurchaseOrderApprovalService.ApprovalView approval(@PathVariable UUID id) {
    return approvals.current(id);
  }

  @PostMapping("/purchase-orders/{id}/submit-approval")
  @PreAuthorize("@permissions.has('purchase_order:update')")
  public PurchaseOrderApprovalService.ApprovalView submitForApproval(@PathVariable UUID id) {
    return approvals.submit(id);
  }

  @PostMapping("/purchase-orders/{id}/approval/approve")
  @PreAuthorize("@permissions.has('approval:act')")
  public PurchaseOrderApprovalService.ApprovalView approvePo(
      @PathVariable UUID id, @RequestBody(required = false) ApprovalCommentRequest request) {
    return approvals.approve(id, request == null ? null : request.comment());
  }

  @PostMapping("/purchase-orders/{id}/approval/reject")
  @PreAuthorize("@permissions.has('approval:act')")
  public PurchaseOrderApprovalService.ApprovalView rejectPo(
      @PathVariable UUID id, @Valid @RequestBody ApprovalCommentRequest request) {
    return approvals.reject(id, request.comment());
  }

  @PostMapping("/purchase-orders/{id}/approval/request-changes")
  @PreAuthorize("@permissions.has('approval:act')")
  public PurchaseOrderApprovalService.ApprovalView requestPoChanges(
      @PathVariable UUID id, @Valid @RequestBody ApprovalCommentRequest request) {
    return approvals.requestChanges(id, request.comment());
  }

  @GetMapping("/purchase-orders/{id}/pdf")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
    PurchaseOrder order = orders.get(id);
    byte[] content = pdf.render(order, vendors.get(order.getVendorId()));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(order.getPoNumber() + ".pdf")
                .build()
                .toString())
        .body(content);
  }

  public record ApprovalCommentRequest(
      @jakarta.validation.constraints.Size(max = 1000) String comment) {}
}
