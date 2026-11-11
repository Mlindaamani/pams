package com.company.procurement.shared.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "pams.mail",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class InMemoryMailAdapter implements MailPort {

  private final List<SentMail> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(String to, String subject, String textBody, String htmlBody) {
    sent.add(new SentMail(to, null, subject, textBody, htmlBody));
  }

  @Override
  public void send(String to, String fromName, String subject, String textBody, String htmlBody) {
    sent.add(new SentMail(to, fromName, subject, textBody, htmlBody));
  }

  public List<SentMail> sentMessages() {
    return List.copyOf(sent);
  }

  public void clear() {
    sent.clear();
  }

  public record SentMail(String to, String fromName, String subject, String body, String html) {}
}
