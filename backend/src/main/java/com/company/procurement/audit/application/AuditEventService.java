package com.company.procurement.audit.application;

import com.company.procurement.audit.domain.AuditEvent;
import com.company.procurement.audit.domain.AuditEventRepository;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records tenant-scoped business audit events. */
@Service
public class AuditEventService {

  private final AuditEventRepository events;
  private final ObjectMapper objectMapper;
  private final CurrentUser currentUser;
  private final Clock clock;

  public AuditEventService(
      AuditEventRepository events,
      ObjectMapper objectMapper,
      CurrentUser currentUser,
      Clock clock) {
    this.events = events;
    this.objectMapper = objectMapper;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional
  public void record(
      String aggregateType,
      UUID aggregateId,
      String action,
      String actorSubject,
      String actorName,
      Map<String, Object> payload) {
    UUID organizationId = TenantContext.require();
    events.save(
        new AuditEvent(
            organizationId,
            aggregateType,
            aggregateId,
            action,
            actorSubject,
            actorName,
            serialize(payload),
            clock.instant()));
  }

  @Transactional
  public void recordCurrentActor(
      String aggregateType, UUID aggregateId, String action, Map<String, Object> payload) {
    record(
        aggregateType, aggregateId, action, currentUser.subject(), currentUser.username(), payload);
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize audit payload", ex);
    }
  }
}
