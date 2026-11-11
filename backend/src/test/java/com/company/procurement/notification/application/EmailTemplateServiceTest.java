package com.company.procurement.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateServiceTest {

  @Test
  void rendersAccountInvitationWithCredentialsAndLoginLink() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    EmailTemplateService templates = new EmailTemplateService(engine);

    String html =
        templates.render(
            "email/account-invitation",
            Map.of(
                "recipientName", "Jane Doe",
                "email", "jane@example.com",
                "temporaryPassword", "temp-password",
                "tenantName", "Acme Ltd",
                "rolesText", "ADMIN",
                "invitationType", "tenant administrator",
                "loginUrl", "http://localhost:3000/login",
                "brandName", "Acme Ltd",
                "brandColor", "#123456",
                "supportEmail", "support@acme.test"));

    assertThat(html)
        .contains("Welcome to PAMS, <span>Jane Doe</span>")
        .contains("jane@example.com")
        .contains("temp-password")
        .contains("Acme Ltd")
        .contains("tenant administrator")
        .contains("http://localhost:3000/login");
  }
}
