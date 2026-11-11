package com.company.procurement.shared.config;

import com.company.procurement.shared.tenant.TenantAwareDataSource;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

  /**
   * Wraps the Boot-configured datasource so RLS session state always matches the current tenant.
   */
  @Bean
  public static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName)
          throws BeansException {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
          return new TenantAwareDataSource(dataSource);
        }
        return bean;
      }
    };
  }
}
