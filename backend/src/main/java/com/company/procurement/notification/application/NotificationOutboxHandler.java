package com.company.procurement.notification.application;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.outbox.OutboxEventHandler;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxHandler implements OutboxEventHandler {

  private final NotificationService notifications;

  public NotificationOutboxHandler(NotificationService notifications) {
    this.notifications = notifications;
  }

  @Override
  public boolean supports(String eventType) {
    return OutboxEventTypes.PURCHASE_ORDER_ISSUED.equals(eventType);
  }

  @Override
  public void handle(OutboxEvent event, Map<String, Object> payload) {
    String requesterSubject = Objects.toString(payload.get("requesterSubject"), null);
    if (requesterSubject == null || requesterSubject.isBlank()) {
      return;
    }
    String poNumber = Objects.toString(payload.get("poNumber"), "");
    String vendorName = Objects.toString(payload.get("vendorName"), "");
    String total = Objects.toString(payload.get("total"), "");
    String currency = Objects.toString(payload.get("currency"), "");
    notifications.createIfAbsent(
        event.getOrganizationId(),
        event.getId(),
        requesterSubject,
        null,
        "PURCHASE_ORDER_ISSUED",
        "Purchase order %s issued".formatted(poNumber),
        "Purchase order %s for %s %s was issued to %s."
            .formatted(poNumber, total, currency, vendorName));
  }
}
