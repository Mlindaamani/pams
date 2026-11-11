package com.company.procurement.shared.config;

import com.company.procurement.shared.error.SecurityProblemResponder;
import com.company.procurement.shared.idempotency.IdempotencyFilter;
import com.company.procurement.shared.tenant.TenantContextFilter;
import com.company.procurement.shared.tenant.TenantSuspensionFilter;
import com.company.procurement.shared.web.RateLimitFilter;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @SuppressWarnings("deprecation")
  @Bean
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      TenantContextFilter tenantContextFilter,
      TenantSuspensionFilter tenantSuspensionFilter,
      IdempotencyFilter idempotencyFilter,
      RateLimitFilter rateLimitFilter,
      SecurityProblemResponder problemResponder)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/v3/api-docs/**",
                        "/openapi.json",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/v1/webhooks/stripe")
                    .permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            problemResponder.unauthorized(request, response, ex))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            problemResponder.forbidden(request, response, ex)))
        .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(tenantSuspensionFilter, TenantContextFilter.class)
        .addFilterAfter(idempotencyFilter, TenantContextFilter.class)
        .addFilterAfter(rateLimitFilter, IdempotencyFilter.class);
    return http.build();
  }

  @Bean
  public TenantContextFilter tenantContextFilter() {
    return new TenantContextFilter();
  }

  @Bean
  public RateLimitFilter rateLimitFilter(
      RateLimitProperties properties,
      com.company.procurement.shared.error.ProblemDetailFactory problemDetailFactory,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    return new RateLimitFilter(properties, problemDetailFactory, objectMapper);
  }

  @Bean
  public TenantSuspensionFilter tenantSuspensionFilter(
      com.company.procurement.shared.tenant.TenantAvailabilityPort statuses,
      com.company.procurement.shared.error.ProblemDetailFactory problemDetailFactory,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    return new TenantSuspensionFilter(statuses, problemDetailFactory, objectMapper);
  }

  @Bean
  public IdempotencyFilter idempotencyFilter(
      com.company.procurement.shared.idempotency.IdempotencyService idempotencyService,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper,
      com.company.procurement.shared.error.ProblemDetailFactory problemDetailFactory) {
    return new IdempotencyFilter(idempotencyService, objectMapper, problemDetailFactory);
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles);
    converter.setPrincipalClaimName("preferred_username");
    return converter;
  }

  private static java.util.Collection<GrantedAuthority> realmRoles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (!(realmAccess instanceof Map<?, ?> map)) {
      return List.of();
    }
    Object roles = map.get("roles");
    if (!(roles instanceof List<?> roleList)) {
      return List.of();
    }
    return roleList.stream()
        .map(Object::toString)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "X-Request-ID",
            "Idempotency-Key",
            "If-Match"));
    configuration.setExposedHeaders(List.of("ETag", "X-Request-ID", HttpHeaders.LOCATION));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
