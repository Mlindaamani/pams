package com.company.procurement.shared.numbering;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcDocumentNumberGenerator implements DocumentNumberGenerator {

  private static final String UPSERT =
      """
      INSERT INTO document_sequence (organization_id, document_type, document_year, next_value)
      VALUES (?, ?, ?, 1)
      ON CONFLICT (organization_id, document_type, document_year)
      DO UPDATE SET next_value = document_sequence.next_value + 1
      RETURNING next_value
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcDocumentNumberGenerator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public String next(UUID organizationId, DocumentType type, int year) {
    Long next = jdbcTemplate.queryForObject(UPSERT, Long.class, organizationId, type.name(), year);
    return "%s-%d-%06d".formatted(type.prefix(), year, next);
  }
}
