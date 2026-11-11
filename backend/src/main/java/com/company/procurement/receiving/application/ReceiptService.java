package com.company.procurement.receiving.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.organization.application.UserProfileLookupService;
import com.company.procurement.purchaseorder.application.PurchaseOrderService;
import com.company.procurement.purchaseorder.application.ReceiptApplicationResult;
import com.company.procurement.receiving.domain.GoodsReceipt;
import com.company.procurement.receiving.domain.GoodsReceiptRepository;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.outbox.OutboxService;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {

  private final GoodsReceiptRepository receipts;
  private final PurchaseOrderService orders;
  private final AuditEventService audit;
  private final OutboxService outbox;
  private final UserProfileLookupService userProfiles;
  private final CurrentUser currentUser;
  private final Clock clock;

  public ReceiptService(
      GoodsReceiptRepository receipts,
      PurchaseOrderService orders,
      AuditEventService audit,
      OutboxService outbox,
      UserProfileLookupService userProfiles,
      CurrentUser currentUser,
      Clock clock) {
    this.receipts = receipts;
    this.orders = orders;
    this.audit = audit;
    this.outbox = outbox;
    this.userProfiles = userProfiles;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional
  public ReceiptResult record(
      UUID purchaseOrderId, long expectedVersion, RecordReceiptCommand command) {
    UUID organizationId = TenantContext.require();
    Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
    for (RecordReceiptCommand.Line line : command.lines()) {
      if (quantities.putIfAbsent(line.purchaseOrderLineId(), line.quantity()) != null) {
        throw new BusinessRuleException("Each purchase order line may appear only once");
      }
    }

    ReceiptApplicationResult order =
        orders.recordReceipt(purchaseOrderId, expectedVersion, quantities);
    LocalDate receivedDate =
        command.receivedDate() == null ? LocalDate.now(clock) : command.receivedDate();
    String recordedByName =
        userProfiles
            .findBySubject(currentUser.subject())
            .map(profile -> profile.getDisplayName())
            .orElseGet(currentUser::username);
    GoodsReceipt receipt =
        new GoodsReceipt(
            organizationId,
            purchaseOrderId,
            receivedDate,
            command.deliveryNoteReference(),
            command.notes(),
            recordedByName);
    for (ReceiptApplicationResult.Line line : order.lines()) {
      if (quantities.containsKey(line.id())) {
        receipt.addLine(line.id(), line.description(), line.unit(), quantities.get(line.id()));
      }
    }
    GoodsReceipt saved = receipts.save(receipt);
    outbox.enqueue(
        OutboxEventTypes.GOODS_RECEIPT_RECORDED,
        "goods_receipt",
        saved.getId(),
        Map.of(
            "receiptId", saved.getId().toString(),
            "purchaseOrderId", purchaseOrderId.toString(),
            "receivedDate", saved.getReceivedDate().toString(),
            "deliveryNoteReference",
                saved.getDeliveryNoteReference() == null ? "" : saved.getDeliveryNoteReference()));
    audit.recordCurrentActor(
        "goods_receipt",
        saved.getId(),
        "goods_receipt_recorded",
        Map.of(
            "purchaseOrderId", purchaseOrderId.toString(),
            "purchaseOrderStatus", order.status().name(),
            "deliveryNoteReference",
                command.deliveryNoteReference() == null ? "" : command.deliveryNoteReference()));
    return new ReceiptResult(saved, order.version());
  }

  @Transactional(readOnly = true)
  public List<GoodsReceipt> list(UUID purchaseOrderId) {
    UUID organizationId = TenantContext.require();
    orders.requireVisible(purchaseOrderId);
    return receipts.findByPurchaseOrder(organizationId, purchaseOrderId);
  }

  @Transactional(readOnly = true)
  public GoodsReceipt get(UUID purchaseOrderId, UUID receiptId) {
    UUID organizationId = TenantContext.require();
    orders.requireVisible(purchaseOrderId);
    return receipts
        .findByIdAndOrganizationId(receiptId, organizationId)
        .filter(receipt -> receipt.getPurchaseOrderId().equals(purchaseOrderId))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Goods receipt %s not found for this purchase order".formatted(receiptId)));
  }

  public record ReceiptResult(GoodsReceipt receipt, long purchaseOrderVersion) {}
}
