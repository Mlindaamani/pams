package com.company.procurement.approval.application;

import com.company.procurement.organization.application.OrganizationService;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ApprovalEscalationJob {

  private static final Logger log = LoggerFactory.getLogger(ApprovalEscalationJob.class);

  private final OrganizationService organizations;
  private final ApprovalService approvals;
  private final Clock clock;

  public ApprovalEscalationJob(
      OrganizationService organizations, ApprovalService approvals, Clock clock) {
    this.organizations = organizations;
    this.approvals = approvals;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${pams.approval.escalation-interval-ms:3600000}",
      initialDelayString = "${pams.approval.escalation-initial-delay-ms:60000}")
  @SchedulerLock(name = "approvalEscalation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void escalateOverdueSteps() {
    for (UUID organizationId : organizations.allOrganizationIds()) {
      TenantContext.set(organizationId);
      try {
        approvals.runEscalation(clock.instant());
      } catch (RuntimeException ex) {
        log.error("Approval escalation failed for organization {}", organizationId, ex);
      } finally {
        TenantContext.clear();
      }
    }
  }
}
