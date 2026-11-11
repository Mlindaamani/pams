package com.company.procurement.subscription.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMrrSnapshotJob {

  private final SubscriptionService subscriptions;

  public SubscriptionMrrSnapshotJob(SubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
  @SchedulerLock(name = "subscriptionMrrSnapshot", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
  public void snapshot() {
    subscriptions.recordMrrSnapshot();
  }
}
