package com.company.procurement.platform.api.dto;

import com.company.procurement.platform.application.PlatformDemoRequestService;
import java.time.Instant;
import java.util.UUID;

public record DemoRequestResponse(
    UUID id,
    String name,
    String email,
    String company,
    String phone,
    String message,
    String status,
    Instant createdAt) {

  public static DemoRequestResponse from(PlatformDemoRequestService.DemoRequestView view) {
    return new DemoRequestResponse(
        view.id(),
        view.name(),
        view.email(),
        view.company(),
        view.phone(),
        view.message(),
        view.status().name(),
        view.createdAt());
  }
}
