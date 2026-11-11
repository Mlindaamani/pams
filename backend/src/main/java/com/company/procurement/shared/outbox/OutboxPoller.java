package com.company.procurement.shared.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxDispatcher dispatcher;

  public OutboxPoller(OutboxDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(
      fixedDelayString = "${pams.outbox.poll-interval-ms:5000}",
      initialDelayString = "${pams.outbox.initial-delay-ms:60000}")
  @SchedulerLock(name = "outboxPoller", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void poll() {
    try {
      int processed = dispatcher.dispatchBatch();
      if (processed > 0) {
        log.info("Outbox dispatched {} event(s)", processed);
      }
    } catch (RuntimeException ex) {
      log.error("Outbox poll failed", ex);
    }
  }
}
