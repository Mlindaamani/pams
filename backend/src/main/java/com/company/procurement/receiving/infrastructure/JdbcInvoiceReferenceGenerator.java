package com.company.procurement.receiving.infrastructure;

import com.company.procurement.receiving.application.InvoiceReferenceGenerator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcInvoiceReferenceGenerator implements InvoiceReferenceGenerator {

  private static final String UPSERT =
      """
      INSERT INTO invoice_sequence (organization_id, fiscal_year, next_value)
      VALUES (?, ?, 1)
      ON CONFLICT (organization_id, fiscal_year)
      DO UPDATE SET next_value = invoice_sequence.next_value + 1
      RETURNING next_value
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcInvoiceReferenceGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public String nextNumber(UUID organizationId, int fiscalYear) {
    Long next = jdbcTemplate.queryForObject(UPSERT, Long.class, organizationId, fiscalYear);
    return "INV-%d-%06d".formatted(fiscalYear, next);
  }
}
