package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxService(OutboxEventRepository events, ObjectMapper objectMapper, Clock clock) {
    this.events = events;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public UUID enqueue(
      String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
    UUID organizationId = TenantContext.require();
    try {
      OutboxEvent event =
          new OutboxEvent(
              organizationId,
              aggregateType,
              aggregateId,
              eventType,
              objectMapper.writeValueAsString(payload),
              clock.instant());
      return events.save(event).getId();
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize outbox payload", ex);
    }
  }
}
