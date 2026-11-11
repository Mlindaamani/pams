package com.company.procurement.organization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class OrganizationPersistenceTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private OrganizationRepository organizations;

  @Autowired private EntityManager entityManager;

  @Test
  void persistsOrganizationAndPopulatesAuditFields() {
    Organization organization =
        organizations.save(new Organization("Acme Ltd", "acme-" + UUID.randomUUID()));
    entityManager.flush();
    entityManager.clear();

    assertThat(organization.getId()).isNotNull();
    assertThat(organization.getVersion()).isZero();

    Organization reloaded = organizations.findById(organization.getId()).orElseThrow();
    assertThat(reloaded.getSlug()).isEqualTo(organization.getSlug());
    assertThat(reloaded.getName()).isEqualTo("Acme Ltd");
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
    assertThat(reloaded.getCreatedBy()).isEqualTo("system");
    assertThat(reloaded.getUpdatedBy()).isEqualTo("system");
  }

  @Test
  void incrementsVersionOnUpdate() {
    Organization organization =
        organizations.save(new Organization("Beta Ltd", "beta-" + UUID.randomUUID()));
    entityManager.flush();
    entityManager.clear();

    Organization reloaded = organizations.findById(organization.getId()).orElseThrow();
    reloaded.rename("Beta Holdings");
    entityManager.flush();
    entityManager.clear();

    Organization updated = organizations.findById(organization.getId()).orElseThrow();
    assertThat(updated.getName()).isEqualTo("Beta Holdings");
    assertThat(updated.getVersion()).isEqualTo(1L);
  }

  @Test
  void findsBySlug() {
    String slug = "gamma-" + UUID.randomUUID();
    organizations.save(new Organization("Gamma Ltd", slug));
    entityManager.flush();

    assertThat(organizations.findBySlug(slug)).isPresent();
    assertThat(organizations.existsBySlug(slug)).isTrue();
    assertThat(organizations.existsBySlug(slug + "-missing")).isFalse();
  }
}
