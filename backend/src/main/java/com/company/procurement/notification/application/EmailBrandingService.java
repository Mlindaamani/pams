package com.company.procurement.notification.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailBrandingService {

  private final JdbcTemplate jdbc;

  public EmailBrandingService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public EmailBranding branding(UUID organizationId) {
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            select coalesce(tb.email_from_name, tb.display_name, o.name, 'PAMS') as brand_name,
                   coalesce(tb.primary_color, '#0B5CAD') as primary_color,
                   tb.support_email
              from organization o
              left join tenant_branding tb on tb.organization_id = o.id
             where o.id = ?
            """,
            organizationId);
    return new EmailBranding(
        (String) row.get("brand_name"),
        (String) row.get("primary_color"),
        (String) row.get("support_email"));
  }

  public record EmailBranding(String brandName, String primaryColor, String supportEmail) {}
}
