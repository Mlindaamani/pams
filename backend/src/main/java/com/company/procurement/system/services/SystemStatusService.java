package com.company.procurement.system.services;

import com.company.procurement.approval.repositories.ApprovalStepRepository;
import com.company.procurement.notification.repositories.NotificationRepository;
import com.company.procurement.shared.repositories.OutboxEventRepository;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.system.dto.SystemStatusResponse;
import com.company.procurement.system.repositories.SystemStatusRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reports operational health and outbox backlog. */
@Service
public class SystemStatusService {

  private final SystemStatusRepository systemStatus;
  private final OutboxEventRepository outboxEvents;
  private final ApprovalStepRepository approvalSteps;
  private final NotificationRepository notifications;
  private final ObjectProvider<BuildProperties> buildProperties;
  private final Clock clock;

  public SystemStatusService(
      SystemStatusRepository systemStatus,
      OutboxEventRepository outboxEvents,
      ApprovalStepRepository approvalSteps,
      NotificationRepository notifications,
      ObjectProvider<BuildProperties> buildProperties,
      Clock clock) {
    this.systemStatus = systemStatus;
    this.outboxEvents = outboxEvents;
    this.approvalSteps = approvalSteps;
    this.notifications = notifications;
    this.buildProperties = buildProperties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SystemStatusResponse status() {
    var organizationId = TenantContext.require();
    String database = systemStatus.databaseAvailable() ? "UP" : "DOWN";
    long outboxPending = outboxEvents.countPending();
    long outboxDead = outboxEvents.countDeadLettered();
    long pendingApprovals = approvalSteps.countPending(organizationId);
    long unreadNotifications = notifications.countUnread(organizationId);
    BuildProperties build = buildProperties.getIfAvailable();
    String version =
        build != null
            ? build.getVersion()
            : getClass().getPackage().getImplementationVersion() == null
                ? "dev"
                : getClass().getPackage().getImplementationVersion();
    String buildTime = build != null && build.getTime() != null ? build.getTime().toString() : null;
    return new SystemStatusResponse(
        "DOWN".equals(database) ? "DEGRADED" : "UP",
        database,
        outboxPending,
        outboxDead,
        pendingApprovals,
        unreadNotifications,
        version,
        buildTime,
        clock.instant());
  }
}
