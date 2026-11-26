package com.company.procurement.organization.infrastructure;

import com.company.procurement.notification.services.EmailBrandingPort;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.TenantBranding;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.organization.repositories.TenantBrandingRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantEmailBrandingAdapter implements EmailBrandingPort {

  private final OrganizationRepository organizations;
  private final TenantBrandingRepository brandings;

  public TenantEmailBrandingAdapter(
      OrganizationRepository organizations, TenantBrandingRepository brandings) {
    this.organizations = organizations;
    this.brandings = brandings;
  }

  @Override
  public EmailBrandingData branding(UUID organizationId) {
    Organization organization = organizations.findById(organizationId).orElse(null);
    TenantBranding branding = brandings.findById(organizationId).orElse(null);
    String organizationName = organization == null ? null : organization.getName();
    String brandName =
        firstNonBlank(
            branding == null ? null : branding.getEmailFromName(),
            branding == null ? null : branding.getDisplayName(),
            organizationName,
            "PAMS");
    String primaryColor =
        branding == null || branding.getPrimaryColor() == null
            ? "#0B5CAD"
            : branding.getPrimaryColor();
    return new EmailBrandingData(
        brandName, primaryColor, branding == null ? null : branding.getSupportEmail());
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "PAMS";
  }
}
