package com.company.procurement.system.application;

import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.system.dto.SystemStatusResponse;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemStatusService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectProvider<BuildProperties> buildProperties;
  private final Clock clock;

  public SystemStatusService(
      JdbcTemplate jdbcTemplate, ObjectProvider<BuildProperties> buildProperties, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.buildProperties = buildProperties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SystemStatusResponse status() {
    var organizationId = TenantContext.require();
    String database = "UP";
    try {
      jdbcTemplate.queryForObject("select 1", Integer.class);
    } catch (RuntimeException ex) {
      database = "DOWN";
    }
    long outboxPending =
        count(
            "select count(*) from outbox_event where processed_at is null and dead_lettered_at is null");
    long outboxDead = count("select count(*) from outbox_event where dead_lettered_at is not null");
    long pendingApprovals =
        jdbcTemplate.queryForObject(
            "select count(*) from approval_step where organization_id = ? and status = 'PENDING'",
            Long.class,
            organizationId);
    long unreadNotifications =
        jdbcTemplate.queryForObject(
            "select count(*) from notification where organization_id = ? and read_at is null",
            Long.class,
            organizationId);
    BuildProperties build = buildProperties.getIfAvailable();
    String version =
        build != null
            ? build.getVersion()
            : getClass().getPackage().getImplementationVersion() == null
                ? "dev"
                : getClass().getPackage().getImplementationVersion();
    String buildTime = build != null && build.getTime() != null ? build.getTime().toString() : null;
    return new SystemStatusResponse(
        "DOWN".equals(database) ? "DEGRADED" : "UP",
        database,
        outboxPending,
        outboxDead,
        pendingApprovals,
        unreadNotifications,
        version,
        buildTime,
        clock.instant());
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }
}
