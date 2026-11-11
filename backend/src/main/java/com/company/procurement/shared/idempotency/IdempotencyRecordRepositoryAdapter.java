package com.company.procurement.shared.idempotency;

import com.company.procurement.shared.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

  private final JpaIdempotencyRecordRepository jpa;

  public IdempotencyRecordRepositoryAdapter(JpaIdempotencyRecordRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<IdempotencyRecord> findByOrganizationIdAndIdempotencyKey(
      UUID organizationId, String idempotencyKey) {
    return jpa.findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey);
  }

  @Override
  public IdempotencyRecord save(IdempotencyRecord record) {
    return jpa.save(record);
  }
}
