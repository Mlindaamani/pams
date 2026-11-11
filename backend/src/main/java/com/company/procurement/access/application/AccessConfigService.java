package com.company.procurement.access.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.authorization.PermissionService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessConfigService {

  private final JdbcTemplate jdbcTemplate;
  private final PermissionService permissions;
  private final AuditEventService audit;

  public AccessConfigService(
      JdbcTemplate jdbcTemplate, PermissionService permissions, AuditEventService audit) {
    this.jdbcTemplate = jdbcTemplate;
    this.permissions = permissions;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public AccessConfig config() {
    List<PermissionModel> catalog =
        jdbcTemplate.query(
            "select code, description from permission order by code",
            (resultSet, row) ->
                new PermissionModel(
                    resultSet.getString("code"), resultSet.getString("description")));
    List<RoleModel> roles =
        jdbcTemplate.query(
            "select role_name, display_name, description from role_model order by role_name",
            (resultSet, row) ->
                new RoleModel(
                    resultSet.getString("role_name"),
                    resultSet.getString("display_name"),
                    resultSet.getString("description")));
    List<RolePermissions> mappings = new ArrayList<>();
    for (RoleModel role : roles) {
      mappings.add(
          new RolePermissions(
              role.roleName(),
              List.copyOf(
                  new LinkedHashSet<>(
                      jdbcTemplate.queryForList(
                          "select permission_code from role_permission where role_name = ? order by permission_code",
                          String.class,
                          role.roleName())))));
    }
    return new AccessConfig(catalog, roles, mappings);
  }

  @Transactional(readOnly = true)
  public RolePermissionPage permissionsForRole(
      String roleName, String query, String assignment, String module, int page, int size) {
    requireKnownRole(roleName);
    int boundedSize = Math.min(Math.max(size, 1), 200);
    int boundedPage = Math.max(page, 0);
    StringBuilder where = new StringBuilder(" where 1 = 1");
    List<Object> args = new ArrayList<>();
    if (query != null && !query.isBlank()) {
      where.append(" and (lower(p.code) like ? or lower(p.description) like ?)");
      String pattern = "%" + query.trim().toLowerCase() + "%";
      args.add(pattern);
      args.add(pattern);
    }
    if (module != null && !module.isBlank()) {
      where.append(" and split_part(p.code, ':', 1) = ?");
      args.add(module.trim());
    }
    String normalizedAssignment = assignment == null ? "all" : assignment.trim().toLowerCase();
    if ("assigned".equals(normalizedAssignment)) {
      where.append(
          " and exists (select 1 from role_permission rp where rp.role_name = ? and rp.permission_code = p.code)");
      args.add(roleName);
    } else if ("available".equals(normalizedAssignment)) {
      where.append(
          " and not exists (select 1 from role_permission rp where rp.role_name = ? and rp.permission_code = p.code)");
      args.add(roleName);
    }

    Long total =
        jdbcTemplate.queryForObject(
            "select count(*) from permission p" + where, Long.class, args.toArray());
    long totalElements = total == null ? 0 : total;
    int totalPages = (int) Math.ceil((double) totalElements / boundedSize);

    List<Object> pageArgs = new ArrayList<>();
    pageArgs.add(roleName);
    pageArgs.addAll(args);
    pageArgs.add(boundedSize);
    pageArgs.add((long) boundedPage * boundedSize);
    List<PermissionAssignment> rows =
        jdbcTemplate.query(
            """
            select p.code,
                   p.description,
                   split_part(p.code, ':', 1) as module,
                   exists (
                       select 1 from role_permission rp
                        where rp.role_name = ? and rp.permission_code = p.code
                   ) as assigned
              from permission p
            """
                + where
                + " order by module, p.code limit ? offset ?",
            (resultSet, row) ->
                new PermissionAssignment(
                    resultSet.getString("code"),
                    resultSet.getString("description"),
                    resultSet.getString("module"),
                    resultSet.getBoolean("assigned")),
            pageArgs.toArray());

    List<String> modules =
        jdbcTemplate.queryForList(
            "select distinct split_part(code, ':', 1) from permission order by 1", String.class);
    return new RolePermissionPage(
        roleName, modules, rows, boundedPage, boundedSize, totalElements, totalPages);
  }

  @Transactional
  public void setPermission(String roleName, String permissionCode, boolean assigned) {
    requireKnownRole(roleName);
    if ("ADMIN".equals(roleName)) {
      throw new BusinessRuleException(
          "The ADMIN role always has every permission and cannot be edited");
    }
    Integer exists =
        jdbcTemplate.queryForObject(
            "select count(*) from permission where code = ?", Integer.class, permissionCode);
    if (exists == null || exists == 0) {
      throw new ResourceNotFoundException("Permission %s not found".formatted(permissionCode));
    }
    if (assigned) {
      jdbcTemplate.update(
          "insert into role_permission (role_name, permission_code) values (?, ?) on conflict do nothing",
          roleName,
          permissionCode);
    } else {
      jdbcTemplate.update(
          "delete from role_permission where role_name = ? and permission_code = ?",
          roleName,
          permissionCode);
    }
    permissions.evictCache();
    audit.recordCurrentActor(
        "role_permission",
        java.util.UUID.nameUUIDFromBytes(
            roleName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        assigned ? "role_permission_assigned" : "role_permission_revoked",
        Map.of("role", roleName, "permission", permissionCode));
  }

  @Transactional
  public RolePermissions updateRole(String roleName, Set<String> permissionCodes) {
    requireKnownRole(roleName);
    if ("ADMIN".equals(roleName)) {
      throw new BusinessRuleException(
          "The ADMIN role always has every permission and cannot be edited");
    }
    Set<String> requested =
        new LinkedHashSet<>(permissionCodes == null ? Set.of() : permissionCodes);
    if (!requested.isEmpty()) {
      String placeholders =
          requested.stream().map(code -> "?").collect(java.util.stream.Collectors.joining(","));
      Set<String> known =
          Set.copyOf(
              jdbcTemplate.queryForList(
                  "select code from permission where code in (" + placeholders + ")",
                  String.class,
                  requested.toArray()));
      Set<String> unknown = new LinkedHashSet<>(requested);
      unknown.removeAll(known);
      if (!unknown.isEmpty()) {
        throw new BusinessRuleException(
            "Unknown permission code(s): " + String.join(", ", unknown));
      }
    }
    jdbcTemplate.update("delete from role_permission where role_name = ?", roleName);
    requested.forEach(
        code ->
            jdbcTemplate.update(
                "insert into role_permission (role_name, permission_code) values (?, ?)",
                roleName,
                code));
    permissions.evictCache();
    audit.recordCurrentActor(
        "role_permission",
        java.util.UUID.nameUUIDFromBytes(
            roleName.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        "role_permissions_updated",
        Map.of("role", roleName, "permissions", requested));
    return new RolePermissions(roleName, List.copyOf(requested));
  }

  private void requireKnownRole(String roleName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from role_model where role_name = ?", Integer.class, roleName);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("Role %s not found".formatted(roleName));
    }
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
