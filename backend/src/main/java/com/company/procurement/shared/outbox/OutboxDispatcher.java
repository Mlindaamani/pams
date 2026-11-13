package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dispatches outbox events to registered handlers. */
@Service
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 5;

  private final OutboxEventRepository events;
  private final List<OutboxEventHandler> handlers;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  public OutboxDispatcher(
      OutboxEventRepository events,
      List<OutboxEventHandler> handlers,
      ObjectMapper objectMapper,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.events = events;
    this.handlers = handlers;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
  }

  @Transactional
  public int dispatchBatch() {
    List<UUID> ids = events.lockPendingIds(BATCH_SIZE);
    int processed = 0;
    for (UUID id : ids) {
      OutboxEvent event = events.findById(id).orElse(null);
      if (event == null || !event.isPending()) {
        continue;
      }
      UUID previousTenant = TenantContext.get().orElse(null);
      TenantContext.set(event.getOrganizationId());
      jdbcTemplate.queryForObject(
          "select set_config('app.current_org', ?, false)",
          String.class,
          event.getOrganizationId().toString());
      try {
        Map<String, Object> payload =
            objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
        List<OutboxEventHandler> matching =
            handlers.stream()
                .filter(candidate -> candidate.supports(event.getEventType()))
                .toList();
        if (matching.isEmpty()) {
          throw new IllegalStateException("No outbox handler for " + event.getEventType());
        }
        for (OutboxEventHandler handler : matching) {
          handler.handle(event, payload);
        }
        event.markProcessed(clock.instant());
        processed++;
      } catch (Exception ex) {
        log.warn("Outbox event {} ({}) failed", event.getId(), event.getEventType(), ex);
        event.recordFailure(clock.instant(), MAX_ATTEMPTS);
      } finally {
        events.save(event);
        if (previousTenant == null) {
          TenantContext.clear();
        } else {
          TenantContext.set(previousTenant);
        }
      }
    }
    return processed;
  }
}
