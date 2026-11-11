package com.company.procurement.request.application;

import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.approval.domain.ApprovalStep;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.shared.authorization.PermissionService;
import com.company.procurement.shared.security.CurrentUser;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates available actions from database rules, status, ownership and current permissions. */
@Service
public class PurchaseRequestActionService {

  private static final Set<String> OWNER_ONLY = Set.of("EDIT", "DELETE", "SUBMIT", "CANCEL");
  private static final Set<String> APPROVAL_ACTIONS =
      Set.of("APPROVE", "REJECT", "REQUEST_CHANGES");

  private final PurchaseRequestService requests;
  private final ApprovalService approvals;
  private final WorkflowActionRules rules;
  private final PermissionService permissions;
  private final CurrentUser currentUser;

  public PurchaseRequestActionService(
      PurchaseRequestService requests,
      ApprovalService approvals,
      WorkflowActionRules rules,
      PermissionService permissions,
      CurrentUser currentUser) {
    this.requests = requests;
    this.approvals = approvals;
    this.rules = rules;
    this.permissions = permissions;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public PurchaseRequestActionsResponse forRequest(UUID requestId) {
    PurchaseRequest request = requests.requireVisible(requestId);
    boolean owner = request.isOwnedBy(currentUser.subject());
    Optional<ApprovalStep> actionableStep =
        request.getStatus() == com.company.procurement.request.domain.RequestStatus.IN_APPROVAL
            ? approvals.currentActionableStep(requestId)
            : Optional.empty();

    Set<String> actions = new LinkedHashSet<>();
    for (WorkflowActionRules.Rule rule :
        rules.find("purchase_request", request.getStatus().name())) {
      if (!permissions.has(rule.requiredPermission())) {
        continue;
      }
      if (OWNER_ONLY.contains(rule.actionCode()) && !owner) {
        continue;
      }
      if (APPROVAL_ACTIONS.contains(rule.actionCode()) && actionableStep.isEmpty()) {
        continue;
      }
      actions.add(rule.actionCode());
    }

    UUID approvalStepId = actionableStep.map(ApprovalStep::getId).orElse(null);
    if (approvalStepId == null) {
      actions.removeAll(APPROVAL_ACTIONS);
    }
    return new PurchaseRequestActionsResponse(actions.stream().sorted().toList(), approvalStepId);
  }
}
