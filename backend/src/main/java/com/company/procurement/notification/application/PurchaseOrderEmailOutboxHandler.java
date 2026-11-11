package com.company.procurement.notification.application;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.mail.MailPort;
import com.company.procurement.shared.mail.MailProperties;
import com.company.procurement.shared.outbox.OutboxEventHandler;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderEmailOutboxHandler implements OutboxEventHandler {

  private final MailPort mailPort;
  private final EmailTemplateService templates;
  private final EmailBrandingService branding;
  private final MailProperties mailProperties;

  public PurchaseOrderEmailOutboxHandler(
      MailPort mailPort,
      EmailTemplateService templates,
      EmailBrandingService branding,
      MailProperties mailProperties) {
    this.mailPort = mailPort;
    this.templates = templates;
    this.branding = branding;
    this.mailProperties = mailProperties;
  }

  @Override
  public boolean supports(String eventType) {
    return OutboxEventTypes.PURCHASE_ORDER_ISSUED.equals(eventType);
  }

  @Override
  public void handle(OutboxEvent event, Map<String, Object> payload) {
    String email = Objects.toString(payload.get("vendorEmail"), null);
    if (email == null || email.isBlank()) {
      return;
    }
    String poNumber = Objects.toString(payload.get("poNumber"), "");
    String vendorName = Objects.toString(payload.get("vendorName"), "");
    String total = Objects.toString(payload.get("total"), "");
    String currency = Objects.toString(payload.get("currency"), "");
    String requestId = Objects.toString(payload.get("requestId"), "");
    String poId = Objects.toString(payload.get("poId"), "");
    String poUrl = mailProperties.appUrl() + "/purchase-orders/" + poId;
    EmailBrandingService.EmailBranding brand = branding.branding(event.getOrganizationId());
    String subject = brand.brandName() + " purchase order " + poNumber;
    String html =
        templates.render(
            "email/purchase-order-issued",
            Map.ofEntries(
                Map.entry("poNumber", poNumber),
                Map.entry("vendorName", vendorName),
                Map.entry("totalAmount", total),
                Map.entry("currency", currency),
                Map.entry("requestId", requestId),
                Map.entry("poUrl", poUrl),
                Map.entry("brandName", brand.brandName()),
                Map.entry("brandColor", brand.primaryColor()),
                Map.entry(
                    "supportEmail", brand.supportEmail() == null ? "" : brand.supportEmail())));
    String text =
        """
        Dear %s,

        Purchase order %s for %s %s has been issued.

        View it online: %s

        Regards,
        %s Procurement
        """
            .formatted(vendorName, poNumber, total, currency, poUrl, brand.brandName());
    mailPort.send(email, brand.brandName(), subject, text, html);
  }
}
