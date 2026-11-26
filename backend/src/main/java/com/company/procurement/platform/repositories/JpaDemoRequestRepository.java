package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.DemoRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaDemoRequestRepository
    extends JpaRepository<DemoRequest, UUID>, JpaSpecificationExecutor<DemoRequest> {}
