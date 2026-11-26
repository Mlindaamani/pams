package com.company.procurement.platform.services;

import com.company.procurement.platform.domain.PlatformAuditEvent;
import com.company.procurement.platform.repositories.PlatformAuditEventRepository;
import com.company.procurement.shared.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records platform-administration audit events. */
@Service
public class PlatformAuditService {

  private final PlatformAuditEventRepository events;
  private final ObjectMapper objectMapper;
  private final CurrentUser currentUser;
  private final Clock clock;

  public PlatformAuditService(
      PlatformAuditEventRepository events,
      ObjectMapper objectMapper,
      CurrentUser currentUser,
      Clock clock) {
    this.events = events;
    this.objectMapper = objectMapper;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional
  public void record(String action, UUID tenantId, Map<String, Object> payload) {
    try {
      events.save(
          new PlatformAuditEvent(
              currentUser.subject(),
              currentUser.username(),
              action,
              tenantId,
              objectMapper.writeValueAsString(payload == null ? Map.of() : payload),
              clock.instant()));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not record platform audit event", ex);
    }
  }
}
