package com.company.procurement.approval.domain;

import java.util.UUID;

/** In-process domain events published by the approval workflow. */
public final class ApprovalEvents {

  private ApprovalEvents() {}

  public record StepApproved(UUID requestId, UUID stepId, String actorSubject) {}

  public record RequestApproved(UUID requestId) {}

  public record RequestRejected(UUID requestId, String comment) {}

  public record ChangesRequested(UUID requestId, String comment) {}

  public record ReminderSent(UUID requestId, UUID stepId) {}

  public record StepEscalated(UUID requestId, UUID stepId) {}
}
