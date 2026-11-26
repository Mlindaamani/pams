package com.company.procurement.shared.services;

import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.security.RolePermissionLookupPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Resolves permissions from the database role mapping. This is the authorization source of truth
 * for API checks and for permissions exposed to the frontend.
 */
@Service("permissions")
public class PermissionService {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final RolePermissionLookupPort rolePermissions;
  private final CurrentUser currentUser;
  private final Clock clock;
  private final ConcurrentHashMap<String, CachedPermissions> cache = new ConcurrentHashMap<>();

  public PermissionService(
      RolePermissionLookupPort rolePermissions, CurrentUser currentUser, Clock clock) {
    this.rolePermissions = rolePermissions;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  public boolean has(String permission) {
    return currentPermissions().contains(permission);
  }

  public boolean hasAny(String... permissions) {
    Set<String> current = currentPermissions();
    return Arrays.stream(permissions).anyMatch(current::contains);
  }

  public Set<String> currentPermissions() {
    List<String> roles = currentUser.roles();
    if (roles.isEmpty()) {
      return Set.of();
    }
    String key = roles.stream().sorted().collect(Collectors.joining("|"));
    CachedPermissions cached = cache.get(key);
    Instant now = clock.instant();
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.permissions();
    }
    Set<String> permissions = Set.copyOf(rolePermissions.permissionsForRoles(roles));
    cache.put(key, new CachedPermissions(permissions, now.plus(CACHE_TTL)));
    return permissions;
  }

  public void evictCache() {
    cache.clear();
  }

  private record CachedPermissions(Set<String> permissions, Instant expiresAt) {}
}
