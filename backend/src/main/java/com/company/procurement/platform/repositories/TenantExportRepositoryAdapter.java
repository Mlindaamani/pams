package com.company.procurement.platform.repositories;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantExportRepositoryAdapter implements TenantExportRepository {

  private static final Set<String> EXCLUDED_TABLES = Set.of("idempotency_record", "outbox_event");

  private final JdbcTemplate jdbc;

  public TenantExportRepositoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<String> tenantTables() {
    return jdbc
        .queryForList(
            """
        select table_name
          from information_schema.columns
         where table_schema = 'public'
           and column_name = 'organization_id'
         order by table_name
        """,
            String.class)
        .stream()
        .filter(table -> !EXCLUDED_TABLES.contains(table))
        .toList();
  }

  @Override
  public List<String> storageKeys(UUID organizationId) {
    return jdbc.queryForList(
        """
        select storage_key from request_attachment where organization_id = ?
        union
        select logo_object_key from tenant_branding
         where organization_id = ? and logo_object_key is not null
        union
        select favicon_object_key from tenant_branding
         where organization_id = ? and favicon_object_key is not null
        union
        select avatar_object_key from user_profile
         where organization_id = ? and avatar_object_key is not null
        """,
        String.class,
        organizationId,
        organizationId,
        organizationId,
        organizationId);
  }

  @Override
  public void writeOrganization(UUID organizationId, OutputStream output) {
    try {
      String json =
          jdbc.queryForObject(
              "select to_jsonb(o)::text from organization o where o.id = ?",
              String.class,
              organizationId);
      output.write((json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new IllegalStateException("Could not write organization export", ex);
    }
  }

  @Override
  public void writeTable(UUID organizationId, String table, OutputStream output) {
    if (!table.matches("[a-z0-9_]+")) {
      throw new IllegalArgumentException("Invalid export table");
    }
    try {
      output.write('[');
      final boolean[] first = {true};
      jdbc.query(
          "select to_jsonb(t)::text from \"" + table + "\" t where t.organization_id = ?",
          rs -> {
            try {
              if (!first[0]) {
                output.write(',');
              }
              first[0] = false;
              output.write(rs.getString(1).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
              throw new IllegalStateException("Could not write " + table + " export", ex);
            }
          },
          organizationId);
      output.write(']');
    } catch (IOException ex) {
      throw new IllegalStateException("Could not write " + table + " export", ex);
    }
  }
}
