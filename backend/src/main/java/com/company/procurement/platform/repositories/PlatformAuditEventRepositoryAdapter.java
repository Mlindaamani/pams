package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.PlatformAuditEvent;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformAuditEventRepositoryAdapter implements PlatformAuditEventRepository {

  private final JpaPlatformAuditEventRepository jpa;

  public PlatformAuditEventRepositoryAdapter(JpaPlatformAuditEventRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public PlatformAuditEvent save(PlatformAuditEvent event) {
    return jpa.save(event);
  }
}
