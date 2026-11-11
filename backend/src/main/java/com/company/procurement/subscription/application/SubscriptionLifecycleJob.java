package com.company.procurement.subscription.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionLifecycleJob {

  private final SubscriptionLifecycleService lifecycle;

  public SubscriptionLifecycleJob(SubscriptionLifecycleService lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Scheduled(cron = "0 15 7 * * *", zone = "UTC")
  @SchedulerLock(name = "subscriptionLifecycle", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
  public void run() {
    lifecycle.runLifecycleNotifications();
  }
}
