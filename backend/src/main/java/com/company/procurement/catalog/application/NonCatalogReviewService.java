package com.company.procurement.catalog.application;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NonCatalogReviewService {

  private final JdbcTemplate jdbc;

  public NonCatalogReviewService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<NonCatalogLine> pending() {
    UUID organizationId = TenantContext.require();
    return jdbc.query(
        """
        select l.id, r.id request_id, r.request_number, r.requester_name, d.name department_name,
               l.description, l.unit, l.unit_price, l.notes, l.created_at
          from purchase_request_line l
          join purchase_request r on r.id = l.purchase_request_id
          join department d on d.id = r.department_id
         where l.organization_id = ?
           and l.is_non_catalog = true
           and l.catalog_review_status = 'PENDING'
         order by l.created_at desc
        """,
        (rs, row) ->
            new NonCatalogLine(
                rs.getObject("id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getString("request_number"),
                rs.getString("requester_name"),
                rs.getString("department_name"),
                rs.getString("description"),
                rs.getString("unit"),
                rs.getBigDecimal("unit_price"),
                rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant()),
        organizationId);
  }

  @Transactional
  public void resolve(UUID lineId, String status) {
    if (!"ADDED".equals(status) && !"DISMISSED".equals(status)) {
      throw new BusinessRuleException("Review status must be ADDED or DISMISSED");
    }
    UUID organizationId = TenantContext.require();
    int updated =
        jdbc.update(
            "update purchase_request_line set catalog_review_status = ?, updated_at = now(), updated_by = ? where organization_id = ? and id = ? and is_non_catalog = true and catalog_review_status = 'PENDING'",
            status,
            "catalog-review",
            organizationId,
            lineId);
    if (updated == 0) {
      throw new ResourceNotFoundException(
          "Pending non-catalog line %s not found".formatted(lineId));
    }
  }

  public record NonCatalogLine(
      UUID id,
      UUID requestId,
      String requestNumber,
      String requesterName,
      String departmentName,
      String description,
      String unit,
      BigDecimal unitPrice,
      String notes,
      Instant createdAt) {}
}
