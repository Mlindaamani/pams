package com.company.procurement.platform.api;

import com.company.procurement.platform.api.dto.DemoRequestResponse;
import com.company.procurement.platform.application.PlatformDemoRequestService;
import com.company.procurement.platform.domain.DemoRequestStatus;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/demo-requests")
@Tag(name = "Platform demo requests", description = "Public demo lead inbox")
public class PlatformDemoRequestController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "name", "company", "status");

  private final PlatformDemoRequestService requests;

  public PlatformDemoRequestController(PlatformDemoRequestService requests) {
    this.requests = requests;
  }

  @GetMapping
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public PageResponse<DemoRequestResponse> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) DemoRequestStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(requests.search(q, status, pageable), DemoRequestResponse::from);
  }
}
