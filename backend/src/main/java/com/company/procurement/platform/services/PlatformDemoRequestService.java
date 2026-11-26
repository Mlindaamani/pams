package com.company.procurement.platform.services;

import com.company.procurement.platform.domain.DemoRequest;
import com.company.procurement.platform.domain.DemoRequestStatus;
import com.company.procurement.platform.repositories.DemoRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages inbound public demo requests. */
@Service
public class PlatformDemoRequestService {

  private final DemoRequestRepository requests;
  private final Clock clock;

  public PlatformDemoRequestService(DemoRequestRepository requests, Clock clock) {
    this.requests = requests;
    this.clock = clock;
  }

  @Transactional
  public UUID create(CreateDemoRequestCommand command) {
    DemoRequest request =
        new DemoRequest(
            command.name().trim(),
            command.email().trim().toLowerCase(),
            command.company().trim(),
            blankToNull(command.phone()),
            blankToNull(command.message()),
            clock.instant());
    return requests.save(request).getId();
  }

  @Transactional(readOnly = true)
  public Page<DemoRequestView> search(String query, DemoRequestStatus status, Pageable pageable) {
    return requests
        .search(query, status, pageable)
        .map(
            request ->
                new DemoRequestView(
                    request.getId(),
                    request.getName(),
                    request.getEmail(),
                    request.getCompany(),
                    request.getPhone(),
                    request.getMessage(),
                    request.getStatus(),
                    request.getCreatedAt()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record CreateDemoRequestCommand(
      String name, String email, String company, String phone, String message) {}

  public record DemoRequestView(
      UUID id,
      String name,
      String email,
      String company,
      String phone,
      String message,
      DemoRequestStatus status,
      Instant createdAt) {}
}
