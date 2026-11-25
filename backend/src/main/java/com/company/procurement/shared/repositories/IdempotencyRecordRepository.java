package com.company.procurement.shared.repositories;

import com.company.procurement.shared.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository {

  Optional<IdempotencyRecord> findByOrganizationIdAndIdempotencyKey(
      UUID organizationId, String idempotencyKey);

  IdempotencyRecord save(IdempotencyRecord record);
}
