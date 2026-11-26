package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.DemoRequest;
import com.company.procurement.platform.domain.DemoRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port for public demo requests. */
public interface DemoRequestRepository {

  DemoRequest save(DemoRequest request);

  Page<DemoRequest> search(String query, DemoRequestStatus status, Pageable pageable);
}
