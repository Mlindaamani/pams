package com.company.procurement.audit.infrastructure;

import com.company.procurement.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaAuditEventRepository
    extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
