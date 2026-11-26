package com.company.procurement.catalog.services;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues and resolves non-catalog request lines. */
@Service
public class NonCatalogReviewService {

  private final NonCatalogReviewPort reviews;

  public NonCatalogReviewService(NonCatalogReviewPort reviews) {
    this.reviews = reviews;
  }

  @Transactional(readOnly = true)
  public List<NonCatalogLine> pending() {
    UUID organizationId = TenantContext.require();
    return reviews.pending(organizationId).stream()
        .map(
            line ->
                new NonCatalogLine(
                    line.id(),
                    line.requestId(),
                    line.requestNumber(),
                    line.requesterName(),
                    line.departmentName(),
                    line.description(),
                    line.unit(),
                    line.unitPrice(),
                    line.notes(),
                    line.createdAt()))
        .toList();
  }

  @Transactional
  public void resolve(UUID lineId, String status) {
    if (!"ADDED".equals(status) && !"DISMISSED".equals(status)) {
      throw new BusinessRuleException("Review status must be ADDED or DISMISSED");
    }
    if (!reviews.resolve(TenantContext.require(), lineId, status, "catalog-review")) {
      throw new ResourceNotFoundException(
          "Pending non-catalog line %s not found".formatted(lineId));
    }
  }

  public record NonCatalogLine(
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
