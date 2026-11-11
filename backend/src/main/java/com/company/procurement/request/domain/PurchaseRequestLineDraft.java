package com.company.procurement.request.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequestLineDraft(
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    UUID catalogItemId,
    UUID vendorId,
    boolean nonCatalog,
    String notes,
    CatalogReviewStatus catalogReviewStatus) {

  public PurchaseRequestLineDraft(
      String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitPrice,
      UUID catalogItemId) {
    this(
        description,
        quantity,
        unit,
        unitPrice,
        catalogItemId,
        null,
        catalogItemId == null,
        null,
        catalogItemId == null ? CatalogReviewStatus.PENDING : CatalogReviewStatus.NOT_REQUIRED);
  }
}
