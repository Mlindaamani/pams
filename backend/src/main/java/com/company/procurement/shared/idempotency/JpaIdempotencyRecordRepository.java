package com.company.procurement.shared.idempotency;

import com.company.procurement.shared.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaIdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

  Optional<IdempotencyRecord> findByOrganizationIdAndIdempotencyKey(
      UUID organizationId, String idempotencyKey);
}
