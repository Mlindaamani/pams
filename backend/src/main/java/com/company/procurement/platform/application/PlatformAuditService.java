package com.company.procurement.platform.application;

import com.company.procurement.shared.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAuditService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final CurrentUser currentUser;
  private final Clock clock;

  public PlatformAuditService(
      JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, CurrentUser currentUser, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional
  public void record(String action, UUID tenantId, Map<String, Object> payload) {
    try {
      jdbcTemplate.update(
          """
          insert into platform_audit_event
              (actor_subject, actor_name, action, tenant_id, payload, occurred_at)
          values (?, ?, ?, ?, ?::jsonb, ?)
          """,
          currentUser.subject(),
          currentUser.username(),
          action,
          tenantId,
          objectMapper.writeValueAsString(payload == null ? Map.of() : payload),
          java.sql.Timestamp.from(clock.instant()));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not record platform audit event", ex);
    }
  }
}
