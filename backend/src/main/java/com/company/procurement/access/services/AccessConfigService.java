package com.company.procurement.access.services;

import com.company.procurement.access.domain.Permission;
import com.company.procurement.access.domain.RolePermission;
import com.company.procurement.access.domain.RolePermissionId;
import com.company.procurement.access.repositories.PermissionRepository;
import com.company.procurement.access.repositories.RolePermissionRepository;
import com.company.procurement.access.repositories.RoleRepository;
import com.company.procurement.audit.services.AuditEventService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.services.PermissionService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintains role-permission assignments and workflow action rules. */
@Service
public class AccessConfigService {

  private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
  private static final String PLATFORM_PERMISSION_PREFIX = "platform:";

  private final PermissionRepository permissions;
  private final RoleRepository roles;
  private final RolePermissionRepository rolePermissions;
  private final PermissionService permissionService;
  private final AuditEventService audit;

  public AccessConfigService(
      PermissionRepository permissions,
      RoleRepository roles,
      RolePermissionRepository rolePermissions,
      PermissionService permissionService,
      AuditEventService audit) {
    this.permissions = permissions;
    this.roles = roles;
    this.rolePermissions = rolePermissions;
    this.permissionService = permissionService;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public AccessConfig config() {
    List<PermissionModel> catalog =
        permissions.findTenantPermissions().stream()
            .map(
                permission ->
                    new PermissionModel(permission.getCode(), permission.getDescription()))
            .toList();
    List<RoleModel> visibleRoles =
        roles.findTenantRoles().stream()
            .map(
                role ->
                    new RoleModel(role.getRoleName(), role.getDisplayName(), role.getDescription()))
            .toList();
    Map<String, Set<String>> assignments = new LinkedHashMap<>();
    visibleRoles.forEach(role -> assignments.put(role.roleName(), new LinkedHashSet<>()));
    rolePermissions
        .findByRoleNames(assignments.keySet())
        .forEach(
            assignment ->
                assignments
                    .computeIfAbsent(
                        assignment.getId().getRoleName(), ignored -> new LinkedHashSet<>())
                    .add(assignment.getId().getPermissionCode()));
    List<RolePermissions> mappings =
        visibleRoles.stream()
            .map(
                role ->
                    new RolePermissions(
                        role.roleName(), List.copyOf(assignments.get(role.roleName()))))
            .toList();
    return new AccessConfig(catalog, visibleRoles, mappings);
  }

  @Transactional(readOnly = true)
  public RolePermissionPage permissionsForRole(
      String roleName, String query, String assignment, String module, int page, int size) {
    requireTenantRole(roleName);
    int boundedSize = Math.min(Math.max(size, 1), 200);
    int boundedPage = Math.max(page, 0);
    String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    String normalizedModule = module == null ? "" : module.trim().toLowerCase(Locale.ROOT);
    String normalizedAssignment =
        assignment == null ? "all" : assignment.trim().toLowerCase(Locale.ROOT);
    Set<String> assignedCodes = rolePermissions.findPermissionCodesByRoleName(roleName);
    List<Permission> tenantPermissions = permissions.findTenantPermissions();
    List<PermissionAssignment> filtered =
        tenantPermissions.stream()
            .filter(
                permission ->
                    normalizedQuery.isEmpty()
                        || permission.getCode().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || permission
                            .getDescription()
                            .toLowerCase(Locale.ROOT)
                            .contains(normalizedQuery))
            .filter(
                permission ->
                    normalizedModule.isEmpty()
                        || moduleOf(permission.getCode()).equals(normalizedModule))
            .filter(
                permission ->
                    switch (normalizedAssignment) {
                      case "assigned" -> assignedCodes.contains(permission.getCode());
                      case "available" -> !assignedCodes.contains(permission.getCode());
                      default -> true;
                    })
            .map(
                permission ->
                    new PermissionAssignment(
                        permission.getCode(),
                        permission.getDescription(),
                        moduleOf(permission.getCode()),
                        assignedCodes.contains(permission.getCode())))
            .toList();
    int totalElements = filtered.size();
    int totalPages = (int) Math.ceil((double) totalElements / boundedSize);
    int fromIndex = Math.min(boundedPage * boundedSize, totalElements);
    int toIndex = Math.min(fromIndex + boundedSize, totalElements);
    List<String> modules =
        tenantPermissions.stream()
            .map(permission -> moduleOf(permission.getCode()))
            .distinct()
            .sorted()
            .toList();
    return new RolePermissionPage(
        roleName,
        modules,
        filtered.subList(fromIndex, toIndex),
        boundedPage,
        boundedSize,
        totalElements,
        totalPages);
  }

  @Transactional
  public void setPermission(String roleName, String permissionCode, boolean assigned) {
    requireTenantRole(roleName);
    requireEditableRole(roleName);
    if (permissionCode.startsWith(PLATFORM_PERMISSION_PREFIX)) {
      throw new BusinessRuleException("Platform permissions cannot be assigned to tenant roles");
    }
    if (!permissions.existsByCode(permissionCode)) {
      throw new ResourceNotFoundException("Permission %s not found".formatted(permissionCode));
    }
    Set<String> assignedCodes = rolePermissions.findPermissionCodesByRoleName(roleName);
    if (assigned && !assignedCodes.contains(permissionCode)) {
      rolePermissions.save(new RolePermission(roleName, permissionCode));
    } else if (!assigned && assignedCodes.contains(permissionCode)) {
      rolePermissions.deleteById(new RolePermissionId(roleName, permissionCode));
    }
    permissionService.evictCache();
    audit.recordCurrentActor(
        "role_permission",
        UUID.nameUUIDFromBytes(roleName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        assigned ? "role_permission_assigned" : "role_permission_revoked",
        Map.of("role", roleName, "permission", permissionCode));
  }

  @Transactional
  public RolePermissions updateRole(String roleName, Set<String> permissionCodes) {
    requireTenantRole(roleName);
    requireEditableRole(roleName);
    Set<String> requested =
        new LinkedHashSet<>(permissionCodes == null ? Set.of() : permissionCodes);
    if (requested.stream().anyMatch(code -> code.startsWith(PLATFORM_PERMISSION_PREFIX))) {
      throw new BusinessRuleException("Platform permissions cannot be assigned to tenant roles");
    }
    if (!requested.isEmpty()) {
      Set<String> known =
          permissions.findAllByCodeIn(requested).stream()
              .map(Permission::getCode)
              .collect(Collectors.toSet());
      Set<String> unknown = new LinkedHashSet<>(requested);
      unknown.removeAll(known);
      if (!unknown.isEmpty()) {
        throw new BusinessRuleException(
            "Unknown permission code(s): " + String.join(", ", unknown));
      }
    }
    rolePermissions.deleteByRoleName(roleName);
    if (!requested.isEmpty()) {
      rolePermissions.saveAll(
          requested.stream().map(code -> new RolePermission(roleName, code)).toList());
    }
    permissionService.evictCache();
    audit.recordCurrentActor(
        "role_permission",
        UUID.nameUUIDFromBytes(roleName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        "role_permissions_updated",
        Map.of("role", roleName, "permissions", requested));
    return new RolePermissions(roleName, List.copyOf(requested));
  }

  private void requireTenantRole(String roleName) {
    if (PLATFORM_ADMIN_ROLE.equals(roleName) || !roles.existsByRoleName(roleName)) {
      throw new ResourceNotFoundException("Role %s not found".formatted(roleName));
    }
  }

  private static void requireEditableRole(String roleName) {
    if ("ADMIN".equals(roleName)) {
      throw new BusinessRuleException(
          "The ADMIN role always has every permission and cannot be edited");
    }
  }

  private static String moduleOf(String code) {
    int separator = code.indexOf(':');
    return separator < 0 ? code : code.substring(0, separator);
  }

  public record AccessConfig(
      List<PermissionModel> permissions,
      List<RoleModel> roles,
      List<RolePermissions> rolePermissions) {}

  public record PermissionModel(String code, String description) {}

  public record RoleModel(String roleName, String displayName, String description) {}

  public record RolePermissions(String roleName, List<String> permissions) {}

  public record PermissionAssignment(
      String code, String description, String module, boolean assigned) {}

  public record RolePermissionPage(
      String roleName,
      List<String> modules,
      List<PermissionAssignment> permissions,
      int page,
      int size,
      long totalElements,
      int totalPages) {}
}
