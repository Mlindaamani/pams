package com.company.procurement.notification.services;

import java.util.UUID;

/** Resolves the tenant identity used in outbound email. */
public interface EmailBrandingPort {

  EmailBrandingData branding(UUID organizationId);

  record EmailBrandingData(String brandName, String primaryColor, String supportEmail) {}
}
