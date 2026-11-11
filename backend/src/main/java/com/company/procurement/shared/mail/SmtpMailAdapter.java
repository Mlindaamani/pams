package com.company.procurement.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pams.mail", name = "enabled", havingValue = "true")
public class SmtpMailAdapter implements MailPort {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  public SmtpMailAdapter(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(String to, String subject, String textBody, String htmlBody) {
    send(to, null, subject, textBody, htmlBody);
  }

  @Override
  public void send(String to, String fromName, String subject, String textBody, String htmlBody) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(
          fromName == null || fromName.isBlank()
              ? properties.from()
              : new InternetAddress(properties.from(), fromName, "UTF-8").toString());
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(textBody, htmlBody);
      mailSender.send(message);
    } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
      throw new IllegalStateException("Could not build email", ex);
    }
  }
}
