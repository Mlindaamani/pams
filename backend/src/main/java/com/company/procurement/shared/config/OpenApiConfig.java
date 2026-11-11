package com.company.procurement.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  public static final String BEARER_AUTH = "bearerAuth";

  @Bean
  public OpenAPI procurementOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Procurement & Approval Management System API")
                .version("v1")
                .description(
                    """
                    Multi-tenant procurement API covering purchase requests, approvals, \
                    purchase orders, receiving and reporting. All endpoints live under \
                    `/api/v1` and return RFC 7807 problems on error.""")
                .license(new License().name("Proprietary")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Keycloak-issued OAuth2 access token.")));
  }
}
