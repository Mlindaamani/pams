package com.company.procurement.catalog.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read and resolve non-catalog request lines awaiting review. */
public interface NonCatalogReviewPort {

  List<PendingLine> pending(UUID organizationId);

  boolean resolve(UUID organizationId, UUID lineId, String status, String actor);

  record PendingLine(
      UUID id,
      UUID requestId,
      String requestNumber,
      String requesterName,
      String departmentName,
      String description,
      String unit,
      BigDecimal unitPrice,
      String notes,
      Instant createdAt) {}
}
