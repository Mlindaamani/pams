package com.company.procurement.audit.api;

import com.company.procurement.audit.application.AuditQueryService;
import com.company.procurement.audit.application.dto.AuditPageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit log", description = "Append-only business audit trail with keyset pagination")
@PreAuthorize("@permissions.has('audit:read')")
public class AuditEventController {

  private final AuditQueryService audit;

  public AuditEventController(AuditQueryService audit) {
    this.audit = audit;
  }

  @GetMapping
  public AuditPageResponse page(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) UUID aggregateId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return audit.page(cursor, limit, aggregateType, aggregateId, action, from, to);
  }
}
