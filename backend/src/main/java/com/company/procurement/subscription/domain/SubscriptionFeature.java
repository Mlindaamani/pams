package com.company.procurement.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Configurable subscription feature. */
@Entity
@Table(name = "subscription_feature")
@EntityListeners(AuditingEntityListener.class)
public class SubscriptionFeature {

  @Id
  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(nullable = false, length = 50)
  private String category;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

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

  protected SubscriptionFeature() {
    // for JPA
  }

  public SubscriptionFeature(
      String code,
      String name,
      String description,
      String category,
      boolean active,
      int sortOrder) {
    this.code = Objects.requireNonNull(code, "code");
    update(name, description, category, active, sortOrder);
  }

  public void update(
      String name, String description, String category, boolean active, int sortOrder) {
    this.name = Objects.requireNonNull(name, "name");
    this.description = Objects.requireNonNull(description, "description");
    this.category = Objects.requireNonNull(category, "category");
    this.active = active;
    this.sortOrder = sortOrder;
  }

  public void deactivate() {
    this.active = false;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getCategory() {
    return category;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
