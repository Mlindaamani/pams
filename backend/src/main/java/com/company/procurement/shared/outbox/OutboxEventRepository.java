package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent event);

  Optional<OutboxEvent> findById(UUID id);

  List<UUID> lockPendingIds(int limit);
}
