package com.company.procurement.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Catalog item saved as a favorite by a user. */
@Entity
@Table(name = "catalog_favorite")
@EntityListeners(AuditingEntityListener.class)
public class CatalogFavorite {

  @EmbeddedId private CatalogFavoriteId id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  @CreatedBy
  private String createdBy;

  protected CatalogFavorite() {
    // for JPA
  }

  public CatalogFavorite(UUID organizationId, UUID userProfileId, UUID catalogItemId) {
    this.organizationId = organizationId;
    this.id = new CatalogFavoriteId(userProfileId, catalogItemId);
  }

  public CatalogFavoriteId getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }
}
