package com.company.procurement.organization.services;

import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.shared.usage.TenantReadinessPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes tenant onboarding progress. */
@Service
public class OnboardingService {

  private final TenantReadinessPort readiness;

  public OnboardingService(TenantReadinessPort readiness) {
    this.readiness = readiness;
  }

  @Transactional(readOnly = true)
  public Checklist checklist() {
    TenantReadinessPort.ReadinessSnapshot snapshot = readiness.readiness(TenantContext.require());
    boolean hasDepartment = snapshot.departments() > 0;
    boolean hasTeam = snapshot.activeUsers() > 1;
    boolean hasPolicy = snapshot.activePolicies() > 0;
    boolean hasRequest = snapshot.nonDraftRequests() > 0;
    boolean hasPurchaseOrder = snapshot.nonDraftOrders() > 0;
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
