package com.company.procurement.shared.authorization;

import com.company.procurement.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves permissions from the database role mapping. This is the authorization source of truth
 * for API checks and for permissions exposed to the frontend.
 */
@Service("permissions")
public class PermissionService {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final JdbcTemplate jdbcTemplate;
  private final CurrentUser currentUser;
  private final Clock clock;
  private final ConcurrentHashMap<String, CachedPermissions> cache = new ConcurrentHashMap<>();

  public PermissionService(JdbcTemplate jdbcTemplate, CurrentUser currentUser, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
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
    String placeholders = roles.stream().map(role -> "?").collect(Collectors.joining(","));
    Set<String> permissions =
        Set.copyOf(
            jdbcTemplate.queryForList(
                "select permission_code from role_permission where role_name in ("
                    + placeholders
                    + ")",
                String.class,
                roles.toArray()));
    cache.put(key, new CachedPermissions(permissions, now.plus(CACHE_TTL)));
    return permissions;
  }

  public void evictCache() {
    cache.clear();
  }

  private record CachedPermissions(Set<String> permissions, Instant expiresAt) {}
}
