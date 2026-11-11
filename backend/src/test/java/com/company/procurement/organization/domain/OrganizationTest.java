package com.company.procurement.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class OrganizationTest {

  @Test
  void trimsNameAndSlug() {
    Organization organization = new Organization("  Acme  ", "  acme  ");

    assertThat(organization.getName()).isEqualTo("Acme");
    assertThat(organization.getSlug()).isEqualTo("acme");
  }

  @Test
  void rejectsBlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Organization("  ", "acme"))
        .withMessageContaining("name");
  }

  @Test
  void rejectsBlankSlug() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Organization("Acme", ""))
        .withMessageContaining("slug");
  }
}
