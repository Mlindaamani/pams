package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RlsInventoryTest {

  private static final Map<String, String> RLS_EXEMPTIONS =
      Map.of(
          "outbox_event",
          "Cross-tenant poller claims due rows with SKIP LOCKED; writes are tenant-scoped.",
          "tenant_provisioning",
          "Platform-only provisioning state; the SaaS Creator has no tenant context.",
          "tenant_subscription",
          "Platform billing reads across tenants; tenant API queries filter by organization.",
          "webhook_delivery",
          "Delivery poller dispatches across tenants; API queries filter by organization.",
          "webhook_subscription",
          "Used by the cross-tenant webhook dispatcher; API queries filter by organization.");

  private static final String INVENTORY_SQL =
      """
      select col.table_name,
             cls.relrowsecurity as rls_enabled,
             cls.relforcerowsecurity as rls_forced,
             exists (
               select 1 from pg_policies policy
               where policy.schemaname = 'public'
                 and policy.tablename = col.table_name
                 and policy.policyname = col.table_name || '_tenant_isolation'
             ) as has_policy
        from information_schema.columns col
        join pg_class cls
          on cls.relname = col.table_name
         and cls.relnamespace = 'public'::regnamespace
       where col.table_schema = 'public'
         and col.column_name = 'organization_id'
       order by col.table_name
      """;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void everyTenantTableHasRlsOrDocumentedExemption() {
    List<TableRls> tables =
        jdbcTemplate.query(
            INVENTORY_SQL,
            (rs, rowNum) ->
                new TableRls(
                    rs.getString("table_name"),
                    rs.getBoolean("rls_enabled"),
                    rs.getBoolean("rls_forced"),
                    rs.getBoolean("has_policy")));

    assertThat(tables).isNotEmpty();
    assertThat(tables)
        .extracting(TableRls::table)
        .contains(
            "purchase_request", "vendor", "vendor_invoice", "webhook_delivery", "outbox_event");

    for (TableRls table : tables) {
      if (RLS_EXEMPTIONS.containsKey(table.table())) {
        continue;
      }
      assertThat(table.rlsEnabled()).as("%s must enable RLS", table.table()).isTrue();
      assertThat(table.rlsForced()).as("%s must force RLS", table.table()).isTrue();
      assertThat(table.hasPolicy())
          .as("%s must have its tenant-isolation policy", table.table())
          .isTrue();
    }

    assertThat(tables).extracting(TableRls::table).containsAll(RLS_EXEMPTIONS.keySet());
  }

  private record TableRls(String table, boolean rlsEnabled, boolean rlsForced, boolean hasPolicy) {}
}
