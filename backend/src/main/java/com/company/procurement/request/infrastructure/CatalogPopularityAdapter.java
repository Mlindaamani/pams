package com.company.procurement.request.infrastructure;

import com.company.procurement.catalog.services.CatalogPopularityPort;
import com.company.procurement.request.repositories.PurchaseRequestRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CatalogPopularityAdapter implements CatalogPopularityPort {

  private final PurchaseRequestRepository requests;

  public CatalogPopularityAdapter(PurchaseRequestRepository requests) {
    this.requests = requests;
  }

  @Override
  public List<UUID> popularItemIds(UUID organizationId, UUID departmentId, int limit) {
    return requests.popularCatalogItemIds(organizationId, departmentId, limit);
  }
}
