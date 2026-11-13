package com.company.procurement.audit.api;

import com.company.procurement.audit.application.AuditQueryService;
import com.company.procurement.audit.application.dto.AuditPageResponse;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.RequiresFeature;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** REST API for append-only business audit trail with keyset pagination. */
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

  @GetMapping(value = "/export", produces = "text/csv")
  @RequiresFeature("audit_export")
  public ResponseEntity<StreamingResponseBody> export(
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) UUID aggregateId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    UUID organizationId = TenantContext.require();
    StreamingResponseBody body =
        output ->
            audit.exportCsv(organizationId, output, aggregateType, aggregateId, action, from, to);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(body);
  }
}
