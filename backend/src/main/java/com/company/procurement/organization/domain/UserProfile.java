package com.company.procurement.organization.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "user_profile")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** Tenant user profile linked to a Keycloak subject. */
public class UserProfile extends SoftDeletableEntity {

  @Column(name = "keycloak_subject", nullable = false, length = 200)
  private String keycloakSubject;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "manager_id")
  private UUID managerId;

  @Column(name = "approval_limit_amount", precision = 19, scale = 4)
  private BigDecimal approvalLimitAmount;

  @Column(name = "approval_limit_currency", length = 3)
  private String approvalLimitCurrency;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "avatar_object_key", length = 300)
  private String avatarObjectKey;

  @Column(name = "avatar_content_type", length = 100)
  private String avatarContentType;

  protected UserProfile() {
    // for JPA
  }

  public UserProfile(
      UUID organizationId,
      String keycloakSubject,
      String email,
      String displayName,
      UUID departmentId) {
    super(organizationId);
    this.keycloakSubject = requireText(keycloakSubject, "keycloakSubject");
    this.email = requireText(email, "email");
    this.displayName = requireText(displayName, "displayName");
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
    this.active = true;
  }

  public void updateDetails(String displayName, UUID departmentId) {
    this.displayName = requireText(displayName, "displayName");
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
  }

  public void synchronizeIdentity(String email, String displayName) {
    this.email = requireText(email, "email");
    this.displayName = requireText(displayName, "displayName");
  }

  public void assignManager(UUID newManagerId) {
    this.managerId = newManagerId;
  }

  public void setApprovalLimit(BigDecimal amount, String currency) {
    if (amount == null) {
      this.approvalLimitAmount = null;
      this.approvalLimitCurrency = null;
      return;
    }
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("approval limit must not be negative");
    }
    this.approvalLimitAmount = amount;
    this.approvalLimitCurrency = requireText(currency, "currency");
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void setAvatar(String objectKey, String contentType) {
    this.avatarObjectKey = Objects.requireNonNull(objectKey, "objectKey");
    this.avatarContentType = Objects.requireNonNull(contentType, "contentType");
  }

  public void clearAvatar() {
    this.avatarObjectKey = null;
    this.avatarContentType = null;
  }

  public String getKeycloakSubject() {
    return keycloakSubject;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  public UUID getManagerId() {
    return managerId;
  }

  public BigDecimal getApprovalLimitAmount() {
    return approvalLimitAmount;
  }

  public String getApprovalLimitCurrency() {
    return approvalLimitCurrency;
  }

  public boolean isActive() {
    return active;
  }

  public String getAvatarObjectKey() {
    return avatarObjectKey;
  }

  public String getAvatarContentType() {
    return avatarContentType;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
