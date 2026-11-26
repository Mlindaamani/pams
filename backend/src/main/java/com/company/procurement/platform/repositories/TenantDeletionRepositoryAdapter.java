package com.company.procurement.platform.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantDeletionRepositoryAdapter implements TenantDeletionRepository {

  private static final List<String> TENANT_TABLES_IN_DELETE_ORDER =
      List.of(
          "vendor_payment_allocation",
          "vendor_payment",
          "vendor_invoice_line",
          "vendor_invoice",
          "goods_receipt_line",
          "goods_receipt",
          "purchase_order_approval_step",
          "purchase_order_approval",
          "purchase_order_line",
          "purchase_order",
          "approval_step",
          "approval_instance",
          "request_attachment",
          "purchase_request_line",
          "purchase_request",
          "budget",
          "approval_policy_step",
          "approval_policy",
          "catalog_favorite",
          "catalog_item_department",
          "catalog_item",
          "vendor",
          "webhook_delivery",
          "webhook_subscription",
          "notification",
          "delegation",
          "user_profile",
          "department",
          "audit_event",
          "idempotency_record",
          "outbox_event",
          "document_sequence",
          "invoice_sequence",
          "purchase_order_sequence",
          "tenant_branding",
          "tenant_provisioning",
          "tenant_subscription");

  private final JdbcTemplate jdbc;

  public TenantDeletionRepositoryAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<String> userSubjects(UUID organizationId) {
    return jdbc.queryForList(
        "select keycloak_subject from user_profile where organization_id = ?",
        String.class,
        organizationId);
  }

  @Override
  public void deleteTenant(UUID organizationId) {
    jdbc.update(
        "update user_profile set manager_id = null where organization_id = ?", organizationId);
    for (String table : TENANT_TABLES_IN_DELETE_ORDER) {
      if (!table.matches("[a-z0-9_]+")) {
        throw new IllegalArgumentException("Invalid tenant table " + table);
      }
      jdbc.update("delete from \"" + table + "\" where organization_id = ?", organizationId);
    }
    jdbc.update("delete from organization where id = ?", organizationId);
  }
}
