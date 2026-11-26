package com.company.procurement.platform.api;

import com.company.procurement.platform.api.dto.DemoRequest;
import com.company.procurement.platform.services.PlatformDemoRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for public endpoint for demo request submission. */
@RestController
@RequestMapping("/api/v1/public/demo-requests")
public class PublicDemoRequestController {

  private final PlatformDemoRequestService requests;

  public PublicDemoRequestController(PlatformDemoRequestService requests) {
    this.requests = requests;
  }

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody DemoRequest request) {
    requests.create(
        new PlatformDemoRequestService.CreateDemoRequestCommand(
            request.name(),
            request.email(),
            request.company(),
            request.phone(),
            request.message()));
    return ResponseEntity.noContent().build();
  }
}
