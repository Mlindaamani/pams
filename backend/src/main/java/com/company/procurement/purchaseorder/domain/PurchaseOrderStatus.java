package com.company.procurement.purchaseorder.domain;

public enum PurchaseOrderStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  ISSUED,
  PARTIALLY_RECEIVED,
  RECEIVED,
  CLOSED,
  CANCELLED
}
