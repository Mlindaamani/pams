package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import java.util.Map;

public interface OutboxEventHandler {

  boolean supports(String eventType);

  void handle(OutboxEvent event, Map<String, Object> payload);
}
