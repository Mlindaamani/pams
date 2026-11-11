package com.company.procurement.shared.mail;

public interface MailPort {

  void send(String to, String subject, String textBody, String htmlBody);

  default void send(String to, String fromName, String subject, String textBody, String htmlBody) {
    send(to, subject, textBody, htmlBody);
  }
}
