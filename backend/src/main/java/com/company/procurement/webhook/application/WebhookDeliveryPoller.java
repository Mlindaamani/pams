package com.company.procurement.webhook.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliveryPoller {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryPoller.class);

  private final WebhookDeliveryDispatcher dispatcher;

  public WebhookDeliveryPoller(WebhookDeliveryDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(
      fixedDelayString = "${pams.webhooks.poll-interval-ms:5000}",
      initialDelayString = "${pams.webhooks.initial-delay-ms:60000}")
  @SchedulerLock(name = "webhookDeliveryPoller", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void poll() {
    try {
      int delivered = dispatcher.dispatchBatch();
      if (delivered > 0) {
        log.info("Delivered {} webhook(s)", delivered);
      }
    } catch (RuntimeException ex) {
      log.error("Webhook delivery poll failed", ex);
    }
  }
}
