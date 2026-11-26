package com.company.procurement.shared.security;

import java.util.Collection;
import java.util.Set;

/** Resolves permission codes for a set of Keycloak roles. */
public interface RolePermissionLookupPort {

  Set<String> permissionsForRoles(Collection<String> roleNames);
}
