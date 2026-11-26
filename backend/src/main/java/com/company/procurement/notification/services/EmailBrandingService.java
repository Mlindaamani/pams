package com.company.procurement.notification.services;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves tenant branding for outbound email. */
@Service
public class EmailBrandingService {

  private final EmailBrandingPort branding;

  public EmailBrandingService(EmailBrandingPort branding) {
    this.branding = branding;
  }

  @Transactional(readOnly = true)
  public EmailBranding branding(UUID organizationId) {
    EmailBrandingPort.EmailBrandingData data = branding.branding(organizationId);
    return new EmailBranding(data.brandName(), data.primaryColor(), data.supportEmail());
  }

  public record EmailBranding(String brandName, String primaryColor, String supportEmail) {}
}
