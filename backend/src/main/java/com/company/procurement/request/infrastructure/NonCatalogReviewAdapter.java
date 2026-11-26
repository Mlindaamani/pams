package com.company.procurement.request.infrastructure;

import com.company.procurement.catalog.services.NonCatalogReviewPort;
import com.company.procurement.request.domain.CatalogReviewStatus;
import com.company.procurement.request.repositories.PurchaseRequestRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NonCatalogReviewAdapter implements NonCatalogReviewPort {

  private final PurchaseRequestRepository requests;

  public NonCatalogReviewAdapter(PurchaseRequestRepository requests) {
    this.requests = requests;
  }

  @Override
  public List<PendingLine> pending(UUID organizationId) {
    return requests.findPendingNonCatalog(organizationId).stream()
        .map(
            row ->
                new PendingLine(
                    row.id(),
                    row.requestId(),
                    row.requestNumber(),
                    row.requesterName(),
                    row.departmentName(),
                    row.description(),
                    row.unit(),
                    row.unitPrice(),
                    row.notes(),
                    row.createdAt()))
        .toList();
  }

  @Override
  public boolean resolve(UUID organizationId, UUID lineId, String status, String actor) {
    return requests.resolveNonCatalogLine(
        organizationId, lineId, CatalogReviewStatus.valueOf(status), actor);
  }
}
