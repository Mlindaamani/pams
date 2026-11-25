package com.company.procurement.audit.repositories;

import com.company.procurement.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEventRepository {

  AuditEvent save(AuditEvent event);

  Page<AuditEvent> search(
      UUID organizationId, String aggregateType, UUID aggregateId, Pageable pageable);
}
