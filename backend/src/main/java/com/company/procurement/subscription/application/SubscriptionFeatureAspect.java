package com.company.procurement.subscription.application;

import com.company.procurement.shared.error.FeatureNotEnabledException;
import com.company.procurement.shared.tenant.TenantContext;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SubscriptionFeatureAspect {

  private final SubscriptionAccessService access;

  public SubscriptionFeatureAspect(SubscriptionAccessService access) {
    this.access = access;
  }

  @Around(
      "@annotation(com.company.procurement.subscription.application.RequiresFeature)"
          + " || @within(com.company.procurement.subscription.application.RequiresFeature)")
  public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    RequiresFeature required = method.getAnnotation(RequiresFeature.class);
    if (required != null) {
      assertEnabled(required.value());
    }
    Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
    RequiresFeature typeRequired = targetClass.getAnnotation(RequiresFeature.class);
    if (typeRequired != null) {
      assertEnabled(typeRequired.value());
    }
    return joinPoint.proceed();
  }

  private void assertEnabled(String featureCode) {
    if (!access.hasFeature(TenantContext.require(), featureCode)) {
      throw new FeatureNotEnabledException(featureCode);
    }
  }
}
