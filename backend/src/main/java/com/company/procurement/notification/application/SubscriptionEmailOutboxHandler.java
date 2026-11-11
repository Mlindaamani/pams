package com.company.procurement.notification.application;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.mail.MailPort;
import com.company.procurement.shared.mail.MailProperties;
import com.company.procurement.shared.outbox.OutboxEventHandler;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionEmailOutboxHandler implements OutboxEventHandler {

  private static final Set<String> SUPPORTED =
      Set.of(
          OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING,
          OutboxEventTypes.SUBSCRIPTION_TRIAL_EXPIRED,
          OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED,
          OutboxEventTypes.SUBSCRIPTION_CANCELED);

  private final MailPort mail;
  private final EmailTemplateService templates;
  private final EmailBrandingService branding;
  private final MailProperties mailProperties;

  public SubscriptionEmailOutboxHandler(
      MailPort mail,
      EmailTemplateService templates,
      EmailBrandingService branding,
      MailProperties mailProperties) {
    this.mail = mail;
    this.templates = templates;
    this.branding = branding;
    this.mailProperties = mailProperties;
  }

  @Override
  public boolean supports(String eventType) {
    return SUPPORTED.contains(eventType);
  }

  @Override
  public void handle(OutboxEvent event, Map<String, Object> payload) {
    String email = Objects.toString(payload.get("email"), null);
    if (email == null || email.isBlank()) return;
    String tenantName = Objects.toString(payload.get("tenantName"), "Tenant");
    String recipientName = Objects.toString(payload.get("recipientName"), "Administrator");
    String planName = Objects.toString(payload.get("planName"), "PAMS");
    String statusLabel = Objects.toString(payload.get("statusLabel"), "");
    String dateLabel = Objects.toString(payload.get("dateLabel"), "—");
    String heading;
    String message;
    String subject;
    String note;
    switch (event.getEventType()) {
      case OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING -> {
        heading = "Your PAMS trial is ending";
        subject = "Your PAMS trial ends soon";
        message =
            "Your trial for %s ends on %s. Choose a plan to keep invoices, receipts, webhooks and advanced reporting available."
                .formatted(tenantName, dateLabel);
        note = "Upgrade before the trial ends to avoid interruption.";
      }
      case OutboxEventTypes.SUBSCRIPTION_TRIAL_EXPIRED -> {
        heading = "Your PAMS trial has ended";
        subject = "Your PAMS trial has ended";
        message =
            "Your trial for %s ended on %s. Your workspace is now read-only until you choose a paid plan."
                .formatted(tenantName, dateLabel);
        note = "Choose a plan to restore write access.";
      }
      case OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED -> {
        heading = "PAMS payment failed";
        subject = "Action required: PAMS payment failed";
        message =
            "We could not process the latest payment for %s. Update your payment method to avoid losing access after the grace period."
                .formatted(tenantName);
        note = "Update billing details in the PAMS billing portal.";
      }
      default -> {
        heading = "Your PAMS subscription was canceled";
        subject = "Your PAMS subscription was canceled";
        message =
            "The subscription for %s is canceled. Access to paid features will end at the close of the current period."
                .formatted(tenantName);
        note = "You can reactivate from the billing page.";
      }
    }
    EmailBrandingService.EmailBranding brand = branding.branding(event.getOrganizationId());
    String ctaUrl = mailProperties.appUrl() + "/admin/billing";
    subject = brand.brandName() + ": " + subject;
    String html =
        templates.render(
            "email/subscription-notice",
            Map.ofEntries(
                Map.entry("heading", heading),
                Map.entry("recipientName", recipientName),
                Map.entry("tenantName", tenantName),
                Map.entry("planName", planName),
                Map.entry("statusLabel", statusLabel),
                Map.entry("dateLabel", dateLabel),
                Map.entry("message", message),
                Map.entry("ctaUrl", ctaUrl),
                Map.entry("ctaLabel", "Open billing"),
                Map.entry("note", note),
                Map.entry("brandName", brand.brandName()),
                Map.entry("brandColor", brand.primaryColor()),
                Map.entry(
                    "supportEmail", brand.supportEmail() == null ? "" : brand.supportEmail())));
    String text =
        """
        %s

        %s

        Open billing: %s

        Regards,
        %s Billing
        """
            .formatted(heading, message, ctaUrl, brand.brandName());
    mail.send(email, brand.brandName(), subject, text, html);
  }
}
