package com.company.procurement.platform.api;

import com.company.procurement.platform.api.dto.DemoRequest;
import jakarta.validation.Valid;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for public endpoint for demo request submission.. */
@RestController
@RequestMapping("/api/v1/public/demo-requests")
public class PublicDemoRequestController {

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PublicDemoRequestController(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody DemoRequest request) {
    jdbc.update(
        "insert into demo_request (name, email, company, phone, message, created_at) values (?, ?, ?, ?, ?, ?)",
        request.name().trim(),
        request.email().trim().toLowerCase(),
        request.company().trim(),
        blankToNull(request.phone()),
        blankToNull(request.message()),
        Timestamp.from(clock.instant()));
    return ResponseEntity.noContent().build();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
