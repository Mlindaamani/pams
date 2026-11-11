package com.company.procurement.shared.actor;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Resolves the acting principal for audit fields and soft deletes. Until security lands in Phase 2
 * the underlying {@link AuditorAware} yields {@code system}.
 */
@Component
public class CurrentActorProvider {

  private final AuditorAware<String> auditorAware;

  public CurrentActorProvider(AuditorAware<String> auditorAware) {
    this.auditorAware = auditorAware;
  }

  public String currentActor() {
    return auditorAware.getCurrentAuditor().orElse("system");
  }

  public Optional<String> currentActorIfPresent() {
    return auditorAware.getCurrentAuditor();
  }
}
