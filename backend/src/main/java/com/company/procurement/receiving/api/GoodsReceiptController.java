package com.company.procurement.receiving.api;

import com.company.procurement.receiving.api.dto.GoodsReceiptResponse;
import com.company.procurement.receiving.api.dto.RecordReceiptRequest;
import com.company.procurement.receiving.api.mapper.GoodsReceiptMapper;
import com.company.procurement.receiving.application.ReceiptService;
import com.company.procurement.receiving.application.RecordReceiptCommand;
import com.company.procurement.shared.web.ConditionalRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/purchase-orders/{purchaseOrderId}/receipts")
@Tag(name = "Goods receipts", description = "Partial and complete receipts against purchase orders")
@com.company.procurement.subscription.application.RequiresFeature("receipts")
public class GoodsReceiptController {

  private final ReceiptService receipts;
  private final GoodsReceiptMapper mapper;

  public GoodsReceiptController(ReceiptService receipts, GoodsReceiptMapper mapper) {
    this.receipts = receipts;
    this.mapper = mapper;
  }

  @PostMapping
  @PreAuthorize("@permissions.has('purchase_order:receive')")
  public ResponseEntity<GoodsReceiptResponse> record(
      @PathVariable UUID purchaseOrderId,
      @Valid @RequestBody RecordReceiptRequest request,
      HttpServletRequest httpRequest) {
    ReceiptService.ReceiptResult result =
        receipts.record(
            purchaseOrderId,
            ConditionalRequests.requireIfMatch(httpRequest),
            new RecordReceiptCommand(
                request.receivedDate(),
                request.deliveryNoteReference(),
                request.notes(),
                request.lines().stream()
                    .map(
                        line ->
                            new RecordReceiptCommand.Line(
                                line.purchaseOrderLineId(), line.quantity()))
                    .toList()));
    GoodsReceiptResponse body = mapper.toResponse(result.receipt());
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/purchase-orders/{purchaseOrderId}/receipts/{receiptId}")
            .buildAndExpand(purchaseOrderId, body.id())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ConditionalRequests.etag(result.purchaseOrderVersion()))
        .body(body);
  }

  @GetMapping
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public List<GoodsReceiptResponse> list(@PathVariable UUID purchaseOrderId) {
    return receipts.list(purchaseOrderId).stream().map(mapper::toResponse).toList();
  }

  @GetMapping("/{receiptId}")
  @PreAuthorize("@permissions.has('purchase_order:read')")
  public GoodsReceiptResponse get(
      @PathVariable UUID purchaseOrderId, @PathVariable UUID receiptId) {
    return mapper.toResponse(receipts.get(purchaseOrderId, receiptId));
  }
}
