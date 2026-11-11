package com.company.procurement.shared.tenant;

import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate tenant filter for tenant-scoped use cases. Runs inside the transaction
 * started by the service (transaction advice has higher precedence, see {@code JpaConfig}).
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

  private final EntityManager entityManager;

  public TenantFilterAspect(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Around(
      "@within(org.springframework.stereotype.Service)"
          + " && execution(* com.company.procurement..application..*(..))")
  public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
    TenantContext.get().ifPresent(this::enableFilter);
    return joinPoint.proceed();
  }

  private void enableFilter(UUID organizationId) {
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter(TenantFilter.NAME).setParameter(TenantFilter.PARAM, organizationId);
  }
}
