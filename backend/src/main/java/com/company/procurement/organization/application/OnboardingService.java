package com.company.procurement.organization.application;

import com.company.procurement.shared.tenant.TenantContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes tenant onboarding progress. */
@Service
public class OnboardingService {

  private final JdbcTemplate jdbc;

  public OnboardingService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Checklist checklist() {
    UUID organizationId = TenantContext.require();
    long departments =
        count(
            "select count(*) from department where organization_id = ? and deleted_at is null",
            organizationId);
    long users =
        count(
            "select count(*) from user_profile where organization_id = ? and deleted_at is null and active = true",
            organizationId);
    long policies =
        count(
            "select count(*) from approval_policy where organization_id = ? and active = true and deleted_at is null",
            organizationId);
    long requests =
        count(
            "select count(*) from purchase_request where organization_id = ? and status <> 'DRAFT'",
            organizationId);
    long purchaseOrders =
        count(
            "select count(*) from purchase_order where organization_id = ? and status <> 'DRAFT'",
            organizationId);
    boolean hasDepartment = departments > 0;
    boolean hasTeam = users > 1;
    boolean hasPolicy = policies > 0;
    boolean hasRequest = requests > 0;
    boolean hasPurchaseOrder = purchaseOrders > 0;
    int completed =
        (hasDepartment ? 1 : 0)
            + (hasTeam ? 1 : 0)
            + (hasPolicy ? 1 : 0)
            + (hasRequest ? 1 : 0)
            + (hasPurchaseOrder ? 1 : 0);
    return new Checklist(
        hasDepartment,
        hasTeam,
        hasPolicy,
        hasRequest,
        hasPurchaseOrder,
        completed,
        5,
        completed == 5);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  public record Checklist(
      boolean hasDepartment,
      boolean hasTeam,
      boolean hasApprovalPolicy,
      boolean hasSubmittedRequest,
      boolean hasIssuedPurchaseOrder,
      int completedSteps,
      int totalSteps,
      boolean complete) {}
}
