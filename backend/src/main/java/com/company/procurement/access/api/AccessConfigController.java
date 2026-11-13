package com.company.procurement.access.api;

import com.company.procurement.access.application.AccessConfigService;
import com.company.procurement.access.application.AccessConfigService.AccessConfig;
import com.company.procurement.access.application.AccessConfigService.RolePermissionPage;
import com.company.procurement.access.application.AccessConfigService.RolePermissions;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for database-driven role and permission mappings. */
@RestController
@RequestMapping("/api/v1/admin/access")
@PreAuthorize("@permissions.has('access:manage')")
@Tag(name = "Access configuration", description = "Database-driven role and permission mappings")
public class AccessConfigController {

  private final AccessConfigService access;

  public AccessConfigController(AccessConfigService access) {
    this.access = access;
  }

  @GetMapping
  public AccessConfig config() {
    return access.config();
  }

  @GetMapping("/roles/{roleName}/permissions")
  public RolePermissionPage rolePermissions(
      @PathVariable String roleName,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "all") String assignment,
      @RequestParam(required = false) String module,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "200") int size) {
    return access.permissionsForRole(roleName, q, assignment, module, page, size);
  }

  @PutMapping("/roles/{roleName}/permissions/{permissionCode}")
  public org.springframework.http.ResponseEntity<Void> setPermission(
      @PathVariable String roleName,
      @PathVariable String permissionCode,
      @RequestBody AssignedRequest request) {
    access.setPermission(roleName, permissionCode, request.assigned());
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  @PutMapping("/roles/{roleName}")
  public RolePermissions updateRole(
      @PathVariable String roleName, @Valid @RequestBody UpdateRoleRequest request) {
    return access.updateRole(roleName, request.permissions());
  }

  public record UpdateRoleRequest(@NotNull Set<String> permissions) {}

  public record AssignedRequest(boolean assigned) {}
}
