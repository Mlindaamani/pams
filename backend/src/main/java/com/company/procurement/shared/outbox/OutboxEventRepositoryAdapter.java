package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

  private static final String LOCK_DUE =
      """
      SELECT id FROM outbox_event
       WHERE processed_at IS NULL
         AND dead_lettered_at IS NULL
         AND next_attempt_at <= now()
       ORDER BY created_at ASC
       LIMIT ?
       FOR UPDATE SKIP LOCKED
      """;

  private final JpaOutboxEventRepository jpa;
  private final JdbcTemplate jdbcTemplate;

  public OutboxEventRepositoryAdapter(JpaOutboxEventRepository jpa, JdbcTemplate jdbcTemplate) {
    this.jpa = jpa;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public OutboxEvent save(OutboxEvent event) {
    return jpa.save(event);
  }

  @Override
  public Optional<OutboxEvent> findById(UUID id) {
    return jpa.findById(id);
  }

  @Override
  public List<UUID> lockPendingIds(int limit) {
    return jdbcTemplate.queryForList(LOCK_DUE, UUID.class, limit);
  }
}
