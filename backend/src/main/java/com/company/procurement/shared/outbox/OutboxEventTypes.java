package com.company.procurement.shared.outbox;

public final class OutboxEventTypes {

  public static final String PURCHASE_ORDER_ISSUED = "PurchaseOrderIssued";
  public static final String GOODS_RECEIPT_RECORDED = "GoodsReceiptRecorded";
  public static final String VENDOR_INVOICE_RECORDED = "VendorInvoiceRecorded";
  public static final String SUBSCRIPTION_TRIAL_ENDING = "SubscriptionTrialEnding";
  public static final String SUBSCRIPTION_TRIAL_EXPIRED = "SubscriptionTrialExpired";
  public static final String SUBSCRIPTION_PAYMENT_FAILED = "SubscriptionPaymentFailed";
  public static final String SUBSCRIPTION_CANCELED = "SubscriptionCanceled";

  private OutboxEventTypes() {}
}
