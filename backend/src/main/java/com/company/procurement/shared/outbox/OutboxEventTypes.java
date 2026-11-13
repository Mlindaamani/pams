package com.company.procurement.shared.outbox;

public final class OutboxEventTypes {

  public static final String PURCHASE_ORDER_ISSUED = "PurchaseOrderIssued";
  public static final String GOODS_RECEIPT_RECORDED = "GoodsReceiptRecorded";
  public static final String VENDOR_INVOICE_RECORDED = "VendorInvoiceRecorded";
  public static final String REQUEST_SUBMITTED = "RequestSubmitted";
  public static final String REQUEST_APPROVED = "RequestApproved";
  public static final String REQUEST_REJECTED = "RequestRejected";
  public static final String REQUEST_CHANGES_REQUESTED = "RequestChangesRequested";
  public static final String REQUEST_CANCELLED = "RequestCancelled";
  public static final String REQUEST_CONVERTED_TO_PURCHASE_ORDER =
      "RequestConvertedToPurchaseOrder";
  public static final String PURCHASE_ORDER_APPROVED = "PurchaseOrderApproved";
  public static final String PURCHASE_ORDER_CANCELLED = "PurchaseOrderCancelled";
  public static final String VENDOR_INVOICE_APPROVED = "VendorInvoiceApproved";
  public static final String VENDOR_INVOICE_DISPUTED = "VendorInvoiceDisputed";
  public static final String VENDOR_INVOICE_REJECTED = "VendorInvoiceRejected";
  public static final String VENDOR_INVOICE_PAID = "VendorInvoicePaid";
  public static final String VENDOR_PAYMENT_SCHEDULED = "VendorPaymentScheduled";
  public static final String VENDOR_PAYMENT_APPROVED = "VendorPaymentApproved";
  public static final String VENDOR_PAYMENT_PAID = "VendorPaymentPaid";
  public static final String VENDOR_PAYMENT_CANCELLED = "VendorPaymentCancelled";
  public static final String VENDOR_STATUS_CHANGED = "VendorStatusChanged";
  public static final String SUBSCRIPTION_TRIAL_ENDING = "SubscriptionTrialEnding";
  public static final String SUBSCRIPTION_TRIAL_EXPIRED = "SubscriptionTrialExpired";
  public static final String SUBSCRIPTION_PAYMENT_FAILED = "SubscriptionPaymentFailed";
  public static final String SUBSCRIPTION_CANCELED = "SubscriptionCanceled";

  private OutboxEventTypes() {}
}
