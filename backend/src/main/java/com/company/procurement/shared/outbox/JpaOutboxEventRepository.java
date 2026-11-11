package com.company.procurement.shared.outbox;

import com.company.procurement.shared.domain.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaOutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}
