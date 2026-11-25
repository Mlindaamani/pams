package com.company.procurement.organization.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.procurement.identity.services.KeycloakAdminPort;
import com.company.procurement.organization.domain.UserProfile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRoleDirectoryServiceTest {

  @Mock private KeycloakAdminPort keycloak;
  @InjectMocks private UserRoleDirectoryService roles;

  @Test
  void keepsOnlyRolesForRequestedUsers() {
    UserProfile requested = profile("subject-1");
    UserProfile other = profile("subject-2");
    when(keycloak.usersByRole(anyList()))
        .thenReturn(
            Map.of(
                "subject-1", Set.of("ADMIN", "REQUESTER"),
                "subject-3", Set.of("FINANCE")));

    assertThat(roles.rolesFor(List.of(requested, other)))
        .containsOnlyKeys("subject-1")
        .containsEntry("subject-1", Set.of("ADMIN", "REQUESTER"));
    verify(keycloak).usersByRole(anyList());
  }

  @Test
  void returnsNoRolesWhenKeycloakIsUnavailable() {
    when(keycloak.usersByRole(anyList())).thenThrow(new IllegalStateException("Keycloak down"));

    assertThat(roles.rolesFor(List.of(profile("subject-1")))).isEmpty();
  }

  private static UserProfile profile(String subject) {
    return new UserProfile(
        UUID.randomUUID(), subject, subject + "@example.com", "Test User", UUID.randomUUID());
  }
}
