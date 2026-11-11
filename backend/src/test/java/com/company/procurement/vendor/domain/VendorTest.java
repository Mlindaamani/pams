package com.company.procurement.vendor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendorTest {

  private final Vendor vendor = new Vendor(UUID.randomUUID(), "Acme Supplies", "TAX-1");

  @Test
  void newVendorIsActive() {
    assertThat(vendor.getStatus()).isEqualTo(VendorStatus.ACTIVE);
  }

  @Test
  void suspendsAndReactivates() {
    vendor.suspend();
    assertThat(vendor.getStatus()).isEqualTo(VendorStatus.SUSPENDED);

    vendor.activate();
    assertThat(vendor.getStatus()).isEqualTo(VendorStatus.ACTIVE);
  }

  @Test
  void blacklistedVendorCannotBeReactivated() {
    vendor.blacklist();

    assertThatThrownBy(vendor::activate)
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining("blacklisted");
  }

  @Test
  void cannotSuspendInactiveVendor() {
    vendor.suspend();

    assertThatThrownBy(vendor::suspend).isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void cannotBlacklistTwice() {
    vendor.blacklist();

    assertThatThrownBy(vendor::blacklist).isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  void softDeleteKeepsFirstDeletionMetadata() {
    Instant first = Instant.parse("2026-01-01T00:00:00Z");
    vendor.softDelete(first, "alice");
    vendor.softDelete(Instant.parse("2026-02-01T00:00:00Z"), "bob");

    assertThat(vendor.isDeleted()).isTrue();
    assertThat(vendor.getDeletedAt()).isEqualTo(first);
    assertThat(vendor.getDeletedBy()).isEqualTo("alice");
    assertThat(vendor).isInstanceOf(SoftDeletableEntity.class);
  }
}
