package com.company.procurement.platform.services;

import com.company.procurement.identity.services.KeycloakAdminPort;
import com.company.procurement.notification.services.EmailTemplateService;
import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.platform.repositories.TenantProvisioningRepository;
import com.company.procurement.shared.mail.MailPort;
import com.company.procurement.shared.mail.MailProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Provisions Keycloak administrators after tenant creation. */
@Service
public class TenantProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
  private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

  private final TenantProvisioningRepository provisionings;
  private final OrganizationRepository organizations;
  private final KeycloakAdminPort keycloak;
  private final MailPort mail;
  private final MailProperties mailProperties;
  private final EmailTemplateService templates;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public TenantProvisioningService(
      TenantProvisioningRepository provisionings,
      OrganizationRepository organizations,
      KeycloakAdminPort keycloak,
      MailPort mail,
      MailProperties mailProperties,
      EmailTemplateService templates,
      Clock clock) {
    this.provisionings = provisionings;
    this.organizations = organizations;
    this.keycloak = keycloak;
    this.mail = mail;
    this.mailProperties = mailProperties;
    this.templates = templates;
    this.clock = clock;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInviteRequested(TenantAdminInviteRequested event) {
    try {
      String temporaryPassword = temporaryPassword();
      keycloak.createOrResetUser(
          event.organizationId(),
          event.email(),
          event.displayName(),
          ADMIN_ROLES,
          temporaryPassword);
      provisionings
          .findByOrganizationId(event.organizationId())
          .ifPresent(
              provisioning -> {
                provisioning.markActive(clock.instant());
                provisionings.save(provisioning);
              });
      String tenantName =
          organizations
              .findById(event.organizationId())
              .map(Organization::getName)
              .orElse("your organization");
      String loginUrl = mailProperties.appUrl() + "/login";
      String text =
          """
          Hello %s,

          Your %s tenant administrator account has been created.
          Sign-in email: %s
          Temporary password: %s
          Roles: ADMIN

          Sign in at %s and choose a new password.
          """
              .formatted(
                  event.displayName(), tenantName, event.email(), temporaryPassword, loginUrl);
      String html =
          templates.render(
              "email/account-invitation",
              Map.ofEntries(
                  Map.entry("recipientName", event.displayName()),
                  Map.entry("email", event.email()),
                  Map.entry("temporaryPassword", temporaryPassword),
                  Map.entry("tenantName", tenantName),
                  Map.entry("rolesText", "ADMIN"),
                  Map.entry("invitationType", "tenant administrator"),
                  Map.entry("loginUrl", loginUrl),
                  Map.entry("brandName", tenantName),
                  Map.entry("brandColor", "#0B5CAD"),
                  Map.entry("supportEmail", "")));
      mail.send(
          event.email(),
          tenantName,
          "Your " + tenantName + " tenant administrator account",
          text,
          html);
      log.info("Tenant administrator provisioned for organization {}", event.organizationId());
    } catch (RuntimeException ex) {
      log.warn("Tenant administrator provisioning failed for {}", event.organizationId(), ex);
      provisionings
          .findByOrganizationId(event.organizationId())
          .ifPresent(
              provisioning -> {
                provisioning.markFailed(safeMessage(ex), clock.instant());
                provisionings.save(provisioning);
              });
    }
  }

  private String temporaryPassword() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String safeMessage(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
