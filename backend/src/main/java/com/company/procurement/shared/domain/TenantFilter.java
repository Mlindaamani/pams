package com.company.procurement.shared.domain;

/** Names used by the Hibernate tenant filter (second layer of tenant isolation). */
public final class TenantFilter {

  public static final String NAME = "tenantFilter";
  public static final String PARAM = "organizationId";

  private TenantFilter() {}
}
