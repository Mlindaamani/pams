package com.company.procurement.notification.application;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Renders branded email templates. */
@Service
public class EmailTemplateService {

  private final SpringTemplateEngine templateEngine;

  public EmailTemplateService(SpringTemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  public String render(String template, Map<String, Object> variables) {
    Context context = new Context();
    context.setVariables(variables);
    return templateEngine.process(template, context);
  }
}
