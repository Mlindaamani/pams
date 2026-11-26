package com.company.procurement.shared.repositories;

import com.company.procurement.shared.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent event);

  Optional<OutboxEvent> findById(UUID id);

  List<UUID> lockPendingIds(int limit);

  long countPending();

  long countDeadLettered();

  void useTenant(java.util.UUID organizationId);
}
