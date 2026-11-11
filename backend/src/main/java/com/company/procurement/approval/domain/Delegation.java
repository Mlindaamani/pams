package com.company.procurement.approval.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "delegation")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class Delegation extends TenantEntity {

  @Column(name = "delegator_user_id", nullable = false, updatable = false)
  private UUID delegatorUserId;

  @Column(name = "delegate_user_id", nullable = false, updatable = false)
  private UUID delegateUserId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "active", nullable = false)
  private boolean active;

  protected Delegation() {
    // for JPA
  }

  public Delegation(
      UUID organizationId,
      UUID delegatorUserId,
      UUID delegateUserId,
      Instant startsAt,
      Instant endsAt) {
    super(organizationId);
    this.delegatorUserId = Objects.requireNonNull(delegatorUserId, "delegatorUserId");
    this.delegateUserId = Objects.requireNonNull(delegateUserId, "delegateUserId");
    if (delegatorUserId.equals(delegateUserId)) {
      throw new IllegalArgumentException("A user cannot delegate to themselves");
    }
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("endsAt must be after startsAt");
    }
    this.active = true;
  }

  public boolean isActiveAt(Instant moment) {
    return active && !moment.isBefore(startsAt) && moment.isBefore(endsAt);
  }

  public void deactivate() {
    this.active = false;
  }

  public UUID getDelegatorUserId() {
    return delegatorUserId;
  }

  public UUID getDelegateUserId() {
    return delegateUserId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public boolean isActive() {
    return active;
  }
}
