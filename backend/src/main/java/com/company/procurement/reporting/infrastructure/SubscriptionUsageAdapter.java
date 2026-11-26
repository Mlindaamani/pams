package com.company.procurement.reporting.infrastructure;

import com.company.procurement.organization.repositories.DepartmentRepository;
import com.company.procurement.organization.repositories.UserProfileRepository;
import com.company.procurement.request.repositories.PurchaseRequestRepository;
import com.company.procurement.request.repositories.RequestAttachmentRepository;
import com.company.procurement.shared.usage.TenantUsagePort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionUsageAdapter implements TenantUsagePort {

  private final UserProfileRepository users;
  private final DepartmentRepository departments;
  private final PurchaseRequestRepository requests;
  private final RequestAttachmentRepository attachments;
  private final Clock clock;

  public SubscriptionUsageAdapter(
      UserProfileRepository users,
      DepartmentRepository departments,
      PurchaseRequestRepository requests,
      RequestAttachmentRepository attachments,
      Clock clock) {
    this.users = users;
    this.departments = departments;
    this.requests = requests;
    this.attachments = attachments;
    this.clock = clock;
  }

  @Override
  public UsageSnapshot usage(UUID organizationId) {
    LocalDate monthStart = LocalDate.now(clock).withDayOfMonth(1);
    return new UsageSnapshot(
        users.countActive(organizationId),
        departments.countActive(organizationId),
        requests.countCreatedBetween(
            organizationId,
            monthStart.atStartOfDay(ZoneOffset.UTC).toInstant(),
            monthStart.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant()),
        attachments.sumSizeBytes(organizationId));
  }
}
