package com.company.procurement.shared.idempotency;

import com.company.procurement.shared.domain.IdempotencyRecord;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
  private static final Duration RETENTION = Duration.ofHours(24);

  private final IdempotencyRecordRepository records;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public IdempotencyService(
      IdempotencyRecordRepository records, ObjectMapper objectMapper, Clock clock) {
    this.records = records;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Optional<StoredResponse> find(UUID organizationId, String key) {
    return records
        .findByOrganizationIdAndIdempotencyKey(organizationId, key)
        .filter(record -> record.getExpiresAt().isAfter(clock.instant()))
        .map(this::toStoredResponse);
  }

  @Transactional
  public void store(
      UUID organizationId,
      String key,
      String requestHash,
      int status,
      String body,
      Map<String, String> headers) {
    try {
      records.save(
          new IdempotencyRecord(
              organizationId,
              key,
              requestHash,
              status,
              body,
              objectMapper.writeValueAsString(headers),
              clock.instant().plus(RETENTION)));
    } catch (RuntimeException | IOException ex) {
      log.warn("Could not persist idempotency record for key {}", key, ex);
    }
  }

  public UUID currentOrganization() {
    return TenantContext.require();
  }

  private StoredResponse toStoredResponse(IdempotencyRecord record) {
    Map<String, String> headers;
    try {
      headers = objectMapper.readValue(record.getResponseHeaders(), new TypeReference<>() {});
    } catch (IOException ex) {
      headers = Map.of();
    }
    return new StoredResponse(
        record.getRequestHash(), record.getResponseStatus(), record.getResponseBody(), headers);
  }

  public record StoredResponse(
      String requestHash, int status, String body, Map<String, String> headers) {}
}
