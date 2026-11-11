package com.company.procurement.request.application;

import com.company.procurement.approval.domain.ApprovalEvents.ChangesRequested;
import com.company.procurement.approval.domain.ApprovalEvents.RequestApproved;
import com.company.procurement.approval.domain.ApprovalEvents.RequestRejected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Applies approval outcomes to the purchase request in the same transaction as the approval step
 * change. Kept as an event listener so the approval module does not depend on the request module.
 */
@Component
public class RequestApprovalOutcomeListener {

  private final PurchaseRequestService requests;

  public RequestApprovalOutcomeListener(PurchaseRequestService requests) {
    this.requests = requests;
  }

  @EventListener
  public void onApproved(RequestApproved event) {
    requests.markApproved(event.requestId());
  }

  @EventListener
  public void onRejected(RequestRejected event) {
    requests.markRejected(event.requestId());
  }

  @EventListener
  public void onChangesRequested(ChangesRequested event) {
    requests.markChangesRequested(event.requestId());
  }
}
