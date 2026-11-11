package com.company.procurement.audit.application;

import com.company.procurement.audit.application.dto.AuditEventResponse;
import com.company.procurement.audit.application.dto.AuditPageResponse;
import com.company.procurement.shared.error.BadRequestException;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

  private static final int DEFAULT_LIMIT = 25;
  private static final int MAX_LIMIT = 100;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public AuditQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public AuditPageResponse page(
      String cursor,
      Integer limit,
      String aggregateType,
      UUID aggregateId,
      String action,
      Instant from,
      Instant to) {
    UUID organizationId = TenantContext.require();
    int effectiveLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    StringBuilder sql =
        new StringBuilder(
            """
            select id, aggregate_type, aggregate_id, action, actor_subject, actor_name,
                   payload::text as payload, occurred_at
              from audit_event
             where organization_id = ?
            """);
    List<Object> arguments = new ArrayList<>();
    arguments.add(organizationId);
    if (aggregateType != null && !aggregateType.isBlank()) {
      sql.append(" and aggregate_type = ?");
      arguments.add(aggregateType);
    }
    if (aggregateId != null) {
      sql.append(" and aggregate_id = ?");
      arguments.add(aggregateId);
    }
    if (action != null && !action.isBlank()) {
      sql.append(" and action = ?");
      arguments.add(action);
    }
    if (from != null) {
      sql.append(" and occurred_at >= ?");
      arguments.add(java.sql.Timestamp.from(from));
    }
    if (to != null) {
      sql.append(" and occurred_at < ?");
      arguments.add(java.sql.Timestamp.from(to));
    }
    Cursor decoded = cursor == null || cursor.isBlank() ? null : decode(cursor);
    if (decoded != null) {
      sql.append(" and (occurred_at, id) < (?, ?)");
      arguments.add(java.sql.Timestamp.from(decoded.occurredAt()));
      arguments.add(decoded.id());
    }
    sql.append(" order by occurred_at desc, id desc limit ?");
    arguments.add(effectiveLimit + 1);

    List<AuditEventResponse> rows =
        jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) ->
                new AuditEventResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("action"),
                    rs.getString("actor_subject"),
                    rs.getString("actor_name"),
                    parse(rs.getString("payload")),
                    rs.getTimestamp("occurred_at").toInstant()),
            arguments.toArray());

    boolean hasMore = rows.size() > effectiveLimit;
    List<AuditEventResponse> content = hasMore ? rows.subList(0, effectiveLimit) : rows;
    String nextCursor =
        hasMore
            ? encode(
                new Cursor(
                    content.get(content.size() - 1).occurredAt(),
                    content.get(content.size() - 1).id()))
            : null;
    return new AuditPageResponse(content, nextCursor);
  }

  @Transactional(readOnly = true)
  public void exportCsv(
      UUID organizationId,
      OutputStream output,
      String aggregateType,
      UUID aggregateId,
      String action,
      Instant from,
      Instant to) {
    jdbcTemplate.queryForObject(
        "select set_config('app.current_org', ?, true)", String.class, organizationId.toString());
    StringBuilder sql =
        new StringBuilder(
            """
            select id, aggregate_type, aggregate_id, action, actor_subject, actor_name,
                   payload::text as payload, occurred_at
              from audit_event
             where organization_id = ?
            """);
    List<Object> arguments = new ArrayList<>();
    arguments.add(organizationId);
    if (aggregateType != null && !aggregateType.isBlank()) {
      sql.append(" and aggregate_type = ?");
      arguments.add(aggregateType);
    }
    if (aggregateId != null) {
      sql.append(" and aggregate_id = ?");
      arguments.add(aggregateId);
    }
    if (action != null && !action.isBlank()) {
      sql.append(" and action = ?");
      arguments.add(action);
    }
    if (from != null) {
      sql.append(" and occurred_at >= ?");
      arguments.add(java.sql.Timestamp.from(from));
    }
    if (to != null) {
      sql.append(" and occurred_at < ?");
      arguments.add(java.sql.Timestamp.from(to));
    }
    sql.append(" order by occurred_at desc, id desc");

    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    writer.write('\uFEFF');
    writer.println(
        "event_id,occurred_at,action,aggregate_type,aggregate_id,actor_subject,actor_name,payload");
    jdbcTemplate.query(
        sql.toString(),
        (rs, rowNum) -> {
          writer.println(
              String.join(
                  ",",
                  csv(rs.getObject("id", UUID.class).toString()),
                  csv(rs.getTimestamp("occurred_at").toInstant().toString()),
                  csv(rs.getString("action")),
                  csv(rs.getString("aggregate_type")),
                  csv(rs.getObject("aggregate_id", UUID.class).toString()),
                  csv(rs.getString("actor_subject")),
                  csv(rs.getString("actor_name")),
                  csv(rs.getString("payload"))));
          return null;
        },
        arguments.toArray());
    writer.flush();
    if (writer.checkError()) {
      throw new IllegalStateException("Could not stream audit export");
    }
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private JsonNode parse(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid audit payload", ex);
    }
  }

  private static String encode(Cursor cursor) {
    String raw = cursor.occurredAt().toEpochMilli() + ":" + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decode(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split(":", 2);
      return new Cursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), UUID.fromString(parts[1]));
    } catch (RuntimeException ex) {
      throw new BadRequestException("Invalid audit cursor");
    }
  }

  private record Cursor(Instant occurredAt, UUID id) {}
}
