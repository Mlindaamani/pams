package com.company.procurement.shared.tenant;

import com.company.procurement.shared.error.BadRequestException;
import java.util.Optional;
import java.util.UUID;

/**
 * Request-scoped tenant holder. Phase 1 populates it from the {@code X-Organization-Id} header; in
 * Phase 2 the OAuth2 filter resolves it from the JWT claim instead.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(UUID organizationId) {
    CURRENT.set(organizationId);
  }

  public static Optional<UUID> get() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static UUID require() {
    UUID organizationId = CURRENT.get();
    if (organizationId == null) {
      throw new BadRequestException("Organization context is required");
    }
    return organizationId;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
