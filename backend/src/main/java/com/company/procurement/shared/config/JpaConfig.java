package com.company.procurement.shared.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 100)
public class JpaConfig {

  public static final String SYSTEM_AUDITOR = "system";

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
        return Optional.of(jwtAuthentication.getToken().getSubject());
      }
      if (authentication != null && authentication.isAuthenticated()) {
        return Optional.of(authentication.getName());
      }
      return Optional.of(SYSTEM_AUDITOR);
    };
  }
}
