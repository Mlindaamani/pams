package com.company.procurement.organization.application;

import com.company.procurement.identity.application.KeycloakAdminPort;
import com.company.procurement.notification.application.EmailTemplateService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.mail.MailPort;
import com.company.procurement.shared.mail.MailProperties;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Invites users through Keycloak and creates local profiles. */
@Service
public class UserInvitationService {

  private static final Logger log = LoggerFactory.getLogger(UserInvitationService.class);
  private static final Set<String> ALLOWED_ROLES =
      Set.of("ADMIN", "REQUESTER", "APPROVER", "PROCUREMENT_OFFICER", "FINANCE", "AUDITOR");

  private final KeycloakAdminPort keycloak;
  private final UserProfileService users;
  private final MailPort mail;
  private final MailProperties mailProperties;
  private final EmailTemplateService templates;
  private final OrganizationService organizationService;
  private final TenantBrandingService branding;
  private final SecureRandom random = new SecureRandom();

  public UserInvitationService(
      KeycloakAdminPort keycloak,
      UserProfileService users,
      MailPort mail,
      MailProperties mailProperties,
      EmailTemplateService templates,
      OrganizationService organizationService,
      TenantBrandingService branding) {
    this.keycloak = keycloak;
    this.users = users;
    this.mail = mail;
    this.mailProperties = mailProperties;
    this.templates = templates;
    this.organizationService = organizationService;
    this.branding = branding;
  }

  public UserProfile invite(InviteUserCommand command) {
    Set<String> roles =
        command.roles() == null || command.roles().isEmpty()
            ? Set.of("REQUESTER")
            : Set.copyOf(command.roles());
    if (!ALLOWED_ROLES.containsAll(roles)) {
      Set<String> invalid = new java.util.LinkedHashSet<>(roles);
      invalid.removeAll(ALLOWED_ROLES);
      throw new BusinessRuleException(
          "Roles not allowed for tenant invitations: " + String.join(", ", invalid));
    }
    String password = temporaryPassword();
    String subject =
        keycloak.createUser(
            com.company.procurement.shared.tenant.TenantContext.require(),
            command.email(),
            command.displayName(),
            roles,
            password);
    UserProfile profile;
    try {
      profile =
          users.create(
              new CreateUserProfileCommand(
                  subject,
                  command.email(),
                  command.displayName(),
                  command.departmentId(),
                  command.managerId(),
                  command.approvalLimitAmount(),
                  command.approvalLimitCurrency()));
    } catch (RuntimeException ex) {
      try {
        keycloak.deleteUser(subject);
      } catch (RuntimeException cleanupEx) {
        log.warn("Could not remove the Keycloak user after profile creation failed", cleanupEx);
      }
      throw ex;
    }
    String tenantName =
        organizationService
            .get(com.company.procurement.shared.tenant.TenantContext.require())
            .getName();
    String rolesText = String.join(", ", roles);
    String loginUrl = mailProperties.appUrl() + "/login";
    TenantBrandingService.BrandingView brand = branding.current();
    String brandName =
        brand.emailFromName() == null || brand.emailFromName().isBlank()
            ? brand.displayName()
            : brand.emailFromName();
    String text =
        """
        Hello %s,

        Your %s user account for %s has been created.
        Sign-in email: %s
        Temporary password: %s
        Roles: %s

        Sign in at %s and choose a new password.
        """
            .formatted(
                command.displayName(),
                brandName,
                tenantName,
                command.email(),
                password,
                rolesText,
                loginUrl);
    String html =
        templates.render(
            "email/account-invitation",
            Map.ofEntries(
                Map.entry("recipientName", command.displayName()),
                Map.entry("email", command.email()),
                Map.entry("temporaryPassword", password),
                Map.entry("tenantName", tenantName),
                Map.entry("rolesText", rolesText),
                Map.entry("invitationType", "user"),
                Map.entry("loginUrl", loginUrl),
                Map.entry("brandName", brandName),
                Map.entry("brandColor", brand.primaryColor()),
                Map.entry(
                    "supportEmail", brand.supportEmail() == null ? "" : brand.supportEmail())));
    mail.send(command.email(), brandName, brandName + ": You have been invited", text, html);
    return profile;
  }

  private String temporaryPassword() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record InviteUserCommand(
      String email,
      String displayName,
      UUID departmentId,
      UUID managerId,
      Set<String> roles,
      BigDecimal approvalLimitAmount,
      String approvalLimitCurrency) {}
}
