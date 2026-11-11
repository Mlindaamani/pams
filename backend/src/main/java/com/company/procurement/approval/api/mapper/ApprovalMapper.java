package com.company.procurement.approval.api.mapper;

import com.company.procurement.approval.api.dto.ApprovalInstanceResponse;
import com.company.procurement.approval.api.dto.ApprovalStepResponse;
import com.company.procurement.approval.domain.ApprovalInstance;
import com.company.procurement.approval.domain.ApprovalStep;
import com.company.procurement.shared.dto.MoneyDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApprovalMapper {

  public ApprovalInstanceResponse toResponse(ApprovalInstance instance) {
    return new ApprovalInstanceResponse(
        instance.getId(),
        instance.getPurchaseRequestId(),
        instance.getPolicyId(),
        instance.getPolicyVersion(),
        MoneyDto.of(instance.getTotalAmount(), instance.getCurrency()),
        instance.getStatus(),
        instance.getCurrentSequence(),
        instance.getLastComment(),
        instance.getSteps().stream().map(step -> toStep(step, instance)).toList(),
        instance.getCreatedAt(),
        instance.getUpdatedAt(),
        instance.getVersion());
  }

  public ApprovalStepResponse toInboxResponse(ApprovalStep step) {
    return toStep(step, step.getInstance());
  }

  public List<ApprovalStepResponse> toInboxResponses(List<ApprovalStep> steps) {
    return steps.stream().map(this::toInboxResponse).toList();
  }

  private ApprovalStepResponse toStep(ApprovalStep step, ApprovalInstance instance) {
    return new ApprovalStepResponse(
        step.getId(),
        instance.getPurchaseRequestId(),
        step.getSequence(),
        step.getName(),
        step.getApproverType(),
        step.getAssigneeRole(),
        step.getAssigneeUserId(),
        step.getStatus(),
        step.getComment(),
        step.getActedBySubject(),
        step.getActedByName(),
        step.getActedAt(),
        step.getSlaDueAt(),
        step.getReminderSentAt(),
        step.getEscalatedAt(),
        MoneyDto.of(instance.getTotalAmount(), instance.getCurrency()));
  }
}
