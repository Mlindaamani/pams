package com.company.procurement.webhook.domain;

import com.company.procurement.shared.outbox.OutboxEventTypes;
import java.util.LinkedHashSet;
import java.util.Set;

public final class WebhookEventTypes {

  public static final String PURCHASE_ORDER_ISSUED = OutboxEventTypes.PURCHASE_ORDER_ISSUED;
  public static final String GOODS_RECEIPT_RECORDED = OutboxEventTypes.GOODS_RECEIPT_RECORDED;
  public static final String VENDOR_INVOICE_RECORDED = OutboxEventTypes.VENDOR_INVOICE_RECORDED;

  public static final Set<String> ALL =
      new LinkedHashSet<>(
          Set.of(PURCHASE_ORDER_ISSUED, GOODS_RECEIPT_RECORDED, VENDOR_INVOICE_RECORDED));

  private WebhookEventTypes() {}
}
