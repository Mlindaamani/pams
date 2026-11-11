package com.company.procurement.shared.api;

import com.company.procurement.shared.authorization.PermissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Access", description = "Effective permissions for the authenticated user")
public class AccessController {

  private final PermissionService permissions;

  public AccessController(PermissionService permissions) {
    this.permissions = permissions;
  }

  @GetMapping("/permissions")
  public PermissionsResponse permissions() {
    return new PermissionsResponse(permissions.currentPermissions().stream().sorted().toList());
  }

  public record PermissionsResponse(List<String> permissions) {}
}
