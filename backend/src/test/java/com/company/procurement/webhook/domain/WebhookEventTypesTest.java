package com.company.procurement.webhook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookEventTypesTest {

  @Test
  void exposesTheTwentyLifecycleEvents() {
    assertThat(WebhookEventTypes.ALL)
        .hasSize(20)
        .contains(
            "RequestSubmitted",
            "RequestApproved",
            "RequestRejected",
            "RequestChangesRequested",
            "RequestCancelled",
            "RequestConvertedToPurchaseOrder",
            "PurchaseOrderIssued",
            "PurchaseOrderApproved",
            "PurchaseOrderCancelled",
            "GoodsReceiptRecorded",
            "VendorInvoiceRecorded",
            "VendorInvoiceApproved",
            "VendorInvoiceDisputed",
            "VendorInvoiceRejected",
            "VendorInvoicePaid",
            "VendorPaymentScheduled",
            "VendorPaymentApproved",
            "VendorPaymentPaid",
            "VendorPaymentCancelled",
            "VendorStatusChanged");
  }
}
