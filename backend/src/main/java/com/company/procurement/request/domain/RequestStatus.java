package com.company.procurement.request.domain;

public enum RequestStatus {
  DRAFT,
  SUBMITTED,
  IN_APPROVAL,
  APPROVED,
  REJECTED,
  CHANGES_REQUESTED,
  CANCELLED,
  CONVERTED_TO_PO;

  public boolean canTransitionTo(RequestStatus target) {
    return switch (this) {
      case DRAFT -> target == SUBMITTED || target == CANCELLED;
      case SUBMITTED -> target == IN_APPROVAL || target == CANCELLED;
      case IN_APPROVAL -> target == APPROVED || target == REJECTED || target == CHANGES_REQUESTED;
      case CHANGES_REQUESTED -> target == SUBMITTED || target == CANCELLED;
      case APPROVED -> target == CONVERTED_TO_PO || target == CANCELLED;
      case CONVERTED_TO_PO -> target == APPROVED;
      case REJECTED, CANCELLED -> false;
    };
  }

  public boolean isEditable() {
    return this == DRAFT || this == CHANGES_REQUESTED;
  }
}
