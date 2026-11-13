package com.company.procurement.webhook.domain;

import com.company.procurement.shared.outbox.OutboxEventTypes;
import java.util.LinkedHashSet;
import java.util.Set;

public final class WebhookEventTypes {

  public static final String PURCHASE_ORDER_ISSUED = OutboxEventTypes.PURCHASE_ORDER_ISSUED;
  public static final String GOODS_RECEIPT_RECORDED = OutboxEventTypes.GOODS_RECEIPT_RECORDED;
  public static final String VENDOR_INVOICE_RECORDED = OutboxEventTypes.VENDOR_INVOICE_RECORDED;
  public static final String REQUEST_SUBMITTED = OutboxEventTypes.REQUEST_SUBMITTED;
  public static final String REQUEST_APPROVED = OutboxEventTypes.REQUEST_APPROVED;
  public static final String REQUEST_REJECTED = OutboxEventTypes.REQUEST_REJECTED;
  public static final String REQUEST_CHANGES_REQUESTED = OutboxEventTypes.REQUEST_CHANGES_REQUESTED;
  public static final String REQUEST_CANCELLED = OutboxEventTypes.REQUEST_CANCELLED;
  public static final String REQUEST_CONVERTED_TO_PURCHASE_ORDER =
      OutboxEventTypes.REQUEST_CONVERTED_TO_PURCHASE_ORDER;
  public static final String PURCHASE_ORDER_APPROVED = OutboxEventTypes.PURCHASE_ORDER_APPROVED;
  public static final String PURCHASE_ORDER_CANCELLED = OutboxEventTypes.PURCHASE_ORDER_CANCELLED;
  public static final String VENDOR_INVOICE_APPROVED = OutboxEventTypes.VENDOR_INVOICE_APPROVED;
  public static final String VENDOR_INVOICE_DISPUTED = OutboxEventTypes.VENDOR_INVOICE_DISPUTED;
  public static final String VENDOR_INVOICE_REJECTED = OutboxEventTypes.VENDOR_INVOICE_REJECTED;
  public static final String VENDOR_INVOICE_PAID = OutboxEventTypes.VENDOR_INVOICE_PAID;
  public static final String VENDOR_PAYMENT_SCHEDULED = OutboxEventTypes.VENDOR_PAYMENT_SCHEDULED;
  public static final String VENDOR_PAYMENT_APPROVED = OutboxEventTypes.VENDOR_PAYMENT_APPROVED;
  public static final String VENDOR_PAYMENT_PAID = OutboxEventTypes.VENDOR_PAYMENT_PAID;
  public static final String VENDOR_PAYMENT_CANCELLED = OutboxEventTypes.VENDOR_PAYMENT_CANCELLED;
  public static final String VENDOR_STATUS_CHANGED = OutboxEventTypes.VENDOR_STATUS_CHANGED;
  public static final String WEBHOOK_TEST = "WebhookTest";

  public static final Set<String> ALL =
      new LinkedHashSet<>(
          Set.of(
              PURCHASE_ORDER_ISSUED,
              GOODS_RECEIPT_RECORDED,
              VENDOR_INVOICE_RECORDED,
              REQUEST_SUBMITTED,
              REQUEST_APPROVED,
              REQUEST_REJECTED,
              REQUEST_CHANGES_REQUESTED,
              REQUEST_CANCELLED,
              REQUEST_CONVERTED_TO_PURCHASE_ORDER,
              PURCHASE_ORDER_APPROVED,
              PURCHASE_ORDER_CANCELLED,
              VENDOR_INVOICE_APPROVED,
              VENDOR_INVOICE_DISPUTED,
              VENDOR_INVOICE_REJECTED,
              VENDOR_INVOICE_PAID,
              VENDOR_PAYMENT_SCHEDULED,
              VENDOR_PAYMENT_APPROVED,
              VENDOR_PAYMENT_PAID,
              VENDOR_PAYMENT_CANCELLED,
              VENDOR_STATUS_CHANGED));

  private WebhookEventTypes() {}
}
