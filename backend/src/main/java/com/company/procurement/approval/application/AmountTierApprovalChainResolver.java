package com.company.procurement.approval.application;

import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApprovalPolicyRepository;
import com.company.procurement.approval.domain.ApprovalPolicyStep;
import com.company.procurement.approval.domain.ApprovalStepDraft;
import com.company.procurement.approval.domain.ApproverType;
import com.company.procurement.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strategy implementation: pick the active policy whose amount band covers the request total. */
@Component
public class AmountTierApprovalChainResolver implements ApprovalChainResolver {

  private final ApprovalPolicyRepository policies;

  public AmountTierApprovalChainResolver(ApprovalPolicyRepository policies) {
    this.policies = policies;
  }

  @Override
  public ResolvedChain resolve(
      UUID organizationId,
      String entityType,
      BigDecimal amount,
      UUID requesterUserId,
      UUID requesterManagerUserId,
      boolean budgetException) {
    ApprovalPolicy policy =
        policies
            .findActiveByAmount(organizationId, entityType, amount)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "No active approval policy covers amount %s".formatted(amount)));

    List<ApprovalStepDraft> steps = new ArrayList<>();
    if (budgetException) {
      steps.add(
          new ApprovalStepDraft(
              "Finance (budget exception)", ApproverType.ROLE, "FINANCE", null, 24, 0));
    }
    for (ApprovalPolicyStep step : policy.getSteps()) {
      UUID assignee = step.getApproverUserId();
      if (step.getApproverType() == ApproverType.REQUESTER_MANAGER) {
        if (requesterManagerUserId == null) {
          throw new BusinessRuleException("Requesters need a manager for this approval policy");
        }
        assignee = requesterManagerUserId;
      }
      if (step.getApproverType() == ApproverType.USER
          && assignee != null
          && assignee.equals(requesterUserId)) {
        continue;
      }
      steps.add(
          new ApprovalStepDraft(
              step.getName(),
              step.getApproverType(),
              step.getApproverRole(),
              assignee,
              step.getSlaHours(),
              step.getSequence()));
    }
    if (steps.isEmpty()) {
      throw new BusinessRuleException("Approval policy resolved to no eligible steps");
    }
    return new ResolvedChain(policy.getId(), policy.getPolicyVersion(), policy.getName(), steps);
  }
}
