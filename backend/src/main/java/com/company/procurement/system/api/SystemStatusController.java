package com.company.procurement.system.api;

import com.company.procurement.system.application.SystemStatusService;
import com.company.procurement.system.dto.SystemStatusResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system")
@Tag(name = "System", description = "Operational status for administrators")
@PreAuthorize("@permissions.has('system:read')")
public class SystemStatusController {

  private final SystemStatusService status;

  public SystemStatusController(SystemStatusService status) {
    this.status = status;
  }

  @GetMapping("/status")
  public SystemStatusResponse status() {
    return status.status();
  }
}
