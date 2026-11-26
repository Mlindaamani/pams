package com.company.procurement.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogFavoriteId implements Serializable {

  @Column(name = "user_profile_id", nullable = false)
  private UUID userProfileId;

  @Column(name = "catalog_item_id", nullable = false)
  private UUID catalogItemId;

  protected CatalogFavoriteId() {
    // for JPA
  }

  public CatalogFavoriteId(UUID userProfileId, UUID catalogItemId) {
    this.userProfileId = Objects.requireNonNull(userProfileId, "userProfileId");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId");
  }

  public UUID getUserProfileId() {
    return userProfileId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CatalogFavoriteId that)) return false;
    return userProfileId.equals(that.userProfileId) && catalogItemId.equals(that.catalogItemId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userProfileId, catalogItemId);
  }
}
