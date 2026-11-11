package com.company.procurement.reporting.config;

import com.company.procurement.shared.domain.ReportingDataChangedEvent;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ReportCacheEvictor {

  private final CacheManager cacheManager;

  public ReportCacheEvictor(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @EventListener
  public void onReportingDataChanged(ReportingDataChangedEvent event) {
    for (String name :
        List.of(
            CacheConfig.SPEND_CACHE, CacheConfig.CYCLE_TIME_CACHE, CacheConfig.COMMITMENTS_CACHE)) {
      Cache cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
  }
}
