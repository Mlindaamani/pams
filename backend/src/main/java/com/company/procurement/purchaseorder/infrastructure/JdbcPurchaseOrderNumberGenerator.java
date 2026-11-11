package com.company.procurement.purchaseorder.infrastructure;

import com.company.procurement.purchaseorder.domain.PurchaseOrderNumberGenerator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPurchaseOrderNumberGenerator implements PurchaseOrderNumberGenerator {

  private static final String UPSERT =
      """
      INSERT INTO purchase_order_sequence (organization_id, fiscal_year, next_value)
      VALUES (?, ?, 1)
      ON CONFLICT (organization_id, fiscal_year)
      DO UPDATE SET next_value = purchase_order_sequence.next_value + 1
      RETURNING next_value
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcPurchaseOrderNumberGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public String nextNumber(UUID organizationId, int fiscalYear) {
    Long next = jdbcTemplate.queryForObject(UPSERT, Long.class, organizationId, fiscalYear);
    return "PO-%d-%06d".formatted(fiscalYear, next);
  }
}
