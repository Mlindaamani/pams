package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.PlatformAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPlatformAuditEventRepository extends JpaRepository<PlatformAuditEvent, UUID> {}
