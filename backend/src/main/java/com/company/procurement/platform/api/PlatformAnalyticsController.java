package com.company.procurement.platform.api;

import com.company.procurement.platform.application.PlatformAnalyticsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/analytics")
@PreAuthorize("@permissions.has('platform:analytics_read')")
@Tag(name = "Platform analytics", description = "Cross-tenant platform metrics")
public class PlatformAnalyticsController {

  private final PlatformAnalyticsService analytics;

  public PlatformAnalyticsController(PlatformAnalyticsService analytics) {
    this.analytics = analytics;
  }

  @GetMapping("/dashboard")
  public PlatformAnalyticsService.PlatformDashboard dashboard(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    return analytics.dashboard(from, to);
  }
}
