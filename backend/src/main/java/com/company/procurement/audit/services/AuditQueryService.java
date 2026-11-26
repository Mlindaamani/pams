package com.company.procurement.audit.services;

import com.company.procurement.audit.repositories.AuditEventQuery;
import com.company.procurement.audit.repositories.AuditEventRepository;
import com.company.procurement.audit.services.dto.AuditEventResponse;
import com.company.procurement.audit.services.dto.AuditPageResponse;
import com.company.procurement.shared.error.BadRequestException;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queries and exports audit events with tenant isolation. */
@Service
public class AuditQueryService {

  private static final int DEFAULT_LIMIT = 25;
  private static final int MAX_LIMIT = 100;

  private final AuditEventRepository events;
  private final ObjectMapper objectMapper;

  public AuditQueryService(AuditEventRepository events, ObjectMapper objectMapper) {
    this.events = events;
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
    Cursor decoded = cursor == null || cursor.isBlank() ? null : decode(cursor);
    AuditEventQuery query =
        new AuditEventQuery(
            organizationId,
            aggregateType,
            aggregateId,
            action,
            from,
            to,
            decoded == null ? null : decoded.occurredAt(),
            decoded == null ? null : decoded.id());
    List<AuditEventResponse> rows =
        events.search(query, effectiveLimit + 1).stream().map(this::response).toList();
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
    AuditEventQuery query =
        new AuditEventQuery(
            organizationId, aggregateType, aggregateId, action, from, to, null, null);
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    writer.write('\uFEFF');
    writer.println(
        "event_id,occurred_at,action,aggregate_type,aggregate_id,actor_subject,actor_name,payload");
    for (var event : events.export(query)) {
      writer.println(
          String.join(
              ",",
              csv(event.getId().toString()),
              csv(event.getOccurredAt().toString()),
              csv(event.getAction()),
              csv(event.getAggregateType()),
              csv(event.getAggregateId().toString()),
              csv(event.getActorSubject()),
              csv(event.getActorName()),
              csv(event.getPayload())));
    }
    writer.flush();
    if (writer.checkError()) {
      throw new IllegalStateException("Could not stream audit export");
    }
  }

  private AuditEventResponse response(com.company.procurement.audit.domain.AuditEvent event) {
    return new AuditEventResponse(
        event.getId(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getAction(),
        event.getActorSubject(),
        event.getActorName(),
        parse(event.getPayload()),
        event.getOccurredAt());
  }

  private JsonNode parse(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid audit payload", ex);
    }
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    return '"' + value.replace("\"", "\"\"") + '"';
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
