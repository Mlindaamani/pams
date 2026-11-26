package com.company.procurement.catalog.repositories;

import com.company.procurement.catalog.domain.CatalogFavorite;
import com.company.procurement.catalog.domain.CatalogFavoriteId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCatalogFavoriteRepository
    extends JpaRepository<CatalogFavorite, CatalogFavoriteId> {

  List<CatalogFavorite> findByIdUserProfileIdOrderByIdCatalogItemIdAsc(UUID userProfileId);

  void deleteByIdUserProfileIdAndIdCatalogItemId(UUID userProfileId, UUID catalogItemId);
}
