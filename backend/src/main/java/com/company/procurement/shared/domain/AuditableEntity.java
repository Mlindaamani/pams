package com.company.procurement.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Common identity, audit and optimistic-locking fields shared by every persistent aggregate.
 *
 * <p>Tenant-owned aggregates should extend {@link TenantEntity} instead, which adds the {@code
 * organization_id} discriminator.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  @CreatedBy
  private String createdBy;

  @Column(name = "updated_at", nullable = false)
  @LastModifiedDate
  private Instant updatedAt;

  @Column(name = "updated_by", nullable = false, length = 100)
  @LastModifiedBy
  private String updatedBy;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected AuditableEntity() {
    // for JPA
  }

  public UUID getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || !getClass().equals(other.getClass())) {
      return false;
    }
    UUID otherId = ((AuditableEntity) other).id;
    return id != null && id.equals(otherId);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass().getName(), id);
  }
}
