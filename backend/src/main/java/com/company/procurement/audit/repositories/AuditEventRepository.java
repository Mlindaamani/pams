package com.company.procurement.audit.repositories;

import com.company.procurement.audit.domain.AuditEvent;
import java.util.List;

/** Persistence port for audit events. */
public interface AuditEventRepository {

  AuditEvent save(AuditEvent event);

  List<AuditEvent> search(AuditEventQuery query, int limit);

  List<AuditEvent> export(AuditEventQuery query);
}
