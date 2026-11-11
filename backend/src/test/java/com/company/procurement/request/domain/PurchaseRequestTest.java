package com.company.procurement.request.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseRequestTest {

  private final PurchaseRequest request =
      new PurchaseRequest(
          UUID.randomUUID(),
          "subject-1",
          "requester",
          UUID.randomUUID(),
          "Laptops",
          "Needed for new starters",
          LocalDate.now().plusDays(10),
          "TZS",
          false);

  @Test
  void computesLineAndRequestTotalsOnTheServer() {
    request.replaceLines(
        List.of(
            new PurchaseRequestLineDraft(
                "Laptop", new BigDecimal("2"), "EA", new BigDecimal("100.50"), null),
            new PurchaseRequestLineDraft(
                "Dock", new BigDecimal("1"), "EA", new BigDecimal("50.25"), null)));

    assertThat(request.getLines().get(0).getLineTotal()).isEqualByComparingTo("201.0000");
    assertThat(request.getTotalAmount()).isEqualByComparingTo("251.2500");
  }

  @Test
  void submitsDraftAndCannotBeEditedAfterwards() {
    request.replaceLines(
        List.of(
            new PurchaseRequestLineDraft(
                "Laptop", BigDecimal.ONE, "EA", new BigDecimal("10"), null)));

    request.submit(Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    assertThat(request.getSubmittedAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    assertThatThrownBy(
            () ->
                request.replaceLines(
                    List.of(
                        new PurchaseRequestLineDraft(
                            "Other", BigDecimal.ONE, "EA", BigDecimal.TEN, null))))
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void cannotSubmitWithoutLines() {
    assertThatThrownBy(() -> request.submit(Instant.now()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("line item");
  }

  @Test
  void cancelIsAllowedOnlyInCancellableStates() {
    request.cancel();
    assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);

    assertThatThrownBy(request::cancel).isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void tracksBudgetReservationAndException() {
    UUID budgetId = UUID.randomUUID();
    request.markBudgetReserved(budgetId, new BigDecimal("10.00"));

    assertThat(request.getBudgetId()).isEqualTo(budgetId);
    assertThat(request.isBudgetReserved()).isTrue();
    assertThat(request.isBudgetException()).isFalse();

    request.markBudgetException();

    assertThat(request.getBudgetId()).isNull();
    assertThat(request.isBudgetReserved()).isFalse();
    assertThat(request.isBudgetException()).isTrue();
  }

  @Test
  void stateMachineAllowsOnlyDeclaredTransitions() {
    assertThat(RequestStatus.DRAFT.canTransitionTo(RequestStatus.SUBMITTED)).isTrue();
    assertThat(RequestStatus.DRAFT.canTransitionTo(RequestStatus.APPROVED)).isFalse();
    assertThat(RequestStatus.SUBMITTED.canTransitionTo(RequestStatus.IN_APPROVAL)).isTrue();
    assertThat(RequestStatus.IN_APPROVAL.canTransitionTo(RequestStatus.CHANGES_REQUESTED)).isTrue();
    assertThat(RequestStatus.CHANGES_REQUESTED.canTransitionTo(RequestStatus.SUBMITTED)).isTrue();
    assertThat(RequestStatus.APPROVED.canTransitionTo(RequestStatus.CONVERTED_TO_PO)).isTrue();
    assertThat(RequestStatus.CANCELLED.canTransitionTo(RequestStatus.SUBMITTED)).isFalse();
  }
}
