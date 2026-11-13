package com.company.procurement.platform.application;

import com.company.procurement.identity.application.KeycloakAdminPort;
import com.company.procurement.notification.application.EmailTemplateService;
import com.company.procurement.shared.mail.MailPort;
import com.company.procurement.shared.mail.MailProperties;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Provisions Keycloak administrators after tenant creation. */
@Service
public class TenantProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
  private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

  private final JdbcTemplate jdbc;
  private final KeycloakAdminPort keycloak;
  private final MailPort mail;
  private final MailProperties mailProperties;
  private final EmailTemplateService templates;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public TenantProvisioningService(
      JdbcTemplate jdbc,
      KeycloakAdminPort keycloak,
      MailPort mail,
      MailProperties mailProperties,
      EmailTemplateService templates,
      Clock clock) {
    this.jdbc = jdbc;
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
      String subject =
          keycloak.createOrResetUser(
              event.organizationId(),
              event.email(),
              event.displayName(),
              ADMIN_ROLES,
              temporaryPassword);
      long now = clock.millis();
      jdbc.update(
          "update tenant_provisioning set status = 'ACTIVE', completed_at = ?, updated_at = ? where organization_id = ?",
          new Timestamp(now),
          new Timestamp(now),
          event.organizationId());
      String tenantName =
          jdbc.queryForObject(
              "select name from organization where id = ?", String.class, event.organizationId());
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
      long now = clock.millis();
      jdbc.update(
          "update tenant_provisioning set status = 'FAILED', last_error = ?, updated_at = ? where organization_id = ?",
          safeMessage(ex),
          new Timestamp(now),
          event.organizationId());
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
