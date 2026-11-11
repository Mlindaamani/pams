package com.company.procurement.shared.error;

/** Thrown when a state machine transition is not allowed from the current state (HTTP 409). */
public class IllegalStateTransitionException extends ConflictException {

  public IllegalStateTransitionException(String message) {
    super(ErrorCodes.ILLEGAL_STATE_TRANSITION, message);
  }
}
