package com.company.procurement.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provisioning state of a tenant's first administrator. */
@Entity
@Table(name = "tenant_provisioning")
public class TenantProvisioning {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false, unique = true, updatable = false)
  private UUID organizationId;

  @Column(name = "admin_email", nullable = false, length = 320)
  private String adminEmail;

  @Column(name = "admin_name", nullable = false, length = 200)
  private String adminName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TenantProvisioningStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantProvisioning() {
    // for JPA
  }

  public TenantProvisioning(UUID organizationId, String adminEmail, String adminName, Instant now) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.adminEmail = Objects.requireNonNull(adminEmail, "adminEmail");
    this.adminName = Objects.requireNonNull(adminName, "adminName");
    this.status = TenantProvisioningStatus.PENDING;
    this.createdAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public void updateAdministrator(String email, String name, Instant now) {
    this.adminEmail = Objects.requireNonNull(email, "email");
    this.adminName = Objects.requireNonNull(name, "name");
    this.status = TenantProvisioningStatus.PENDING;
    this.lastError = null;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void markPending(Instant now) {
    this.status = TenantProvisioningStatus.PENDING;
    this.lastError = null;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void markActive(Instant now) {
    this.status = TenantProvisioningStatus.ACTIVE;
    this.lastError = null;
    this.completedAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public void markFailed(String error, Instant now) {
    this.status = TenantProvisioningStatus.FAILED;
    this.lastError = error;
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getAdminEmail() {
    return adminEmail;
  }

  public String getAdminName() {
    return adminName;
  }

  public TenantProvisioningStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
