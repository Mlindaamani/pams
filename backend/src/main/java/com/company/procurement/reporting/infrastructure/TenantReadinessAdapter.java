package com.company.procurement.reporting.infrastructure;

import com.company.procurement.approval.repositories.ApprovalPolicyRepository;
import com.company.procurement.organization.repositories.DepartmentRepository;
import com.company.procurement.organization.repositories.UserProfileRepository;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderRepository;
import com.company.procurement.request.repositories.PurchaseRequestRepository;
import com.company.procurement.shared.usage.TenantReadinessPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantReadinessAdapter implements TenantReadinessPort {

  private final DepartmentRepository departments;
  private final UserProfileRepository users;
  private final ApprovalPolicyRepository policies;
  private final PurchaseRequestRepository requests;
  private final PurchaseOrderRepository purchaseOrders;

  public TenantReadinessAdapter(
      DepartmentRepository departments,
      UserProfileRepository users,
      ApprovalPolicyRepository policies,
      PurchaseRequestRepository requests,
      PurchaseOrderRepository purchaseOrders) {
    this.departments = departments;
    this.users = users;
    this.policies = policies;
    this.requests = requests;
    this.purchaseOrders = purchaseOrders;
  }

  @Override
  public ReadinessSnapshot readiness(UUID organizationId) {
    return new ReadinessSnapshot(
        departments.countActive(organizationId),
        users.countActive(organizationId),
        policies.countActive(organizationId),
        requests.countNonDraft(organizationId),
        purchaseOrders.countNonDraft(organizationId));
  }
}
