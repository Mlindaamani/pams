package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.PlatformAuditEvent;

/** Persistence port for platform audit events. */
public interface PlatformAuditEventRepository {

  PlatformAuditEvent save(PlatformAuditEvent event);
}
