package com.company.procurement.organization.application;

import com.company.procurement.organization.domain.DepartmentRepository;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.organization.domain.UserProfileRepository;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.storage.FileStoragePort;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.subscription.application.SubscriptionAccessService;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private static final int MAX_MANAGER_CHAIN = 50;
  private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
  private static final Set<String> AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  private final UserProfileRepository users;
  private final DepartmentRepository departments;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;
  private final CurrentUser currentUser;
  private final FileStoragePort storage;
  private final SubscriptionAccessService subscriptionAccess;

  public UserProfileService(
      UserProfileRepository users,
      DepartmentRepository departments,
      Clock clock,
      CurrentActorProvider actorProvider,
      CurrentUser currentUser,
      FileStoragePort storage,
      SubscriptionAccessService subscriptionAccess) {
    this.users = users;
    this.departments = departments;
    this.clock = clock;
    this.actorProvider = actorProvider;
    this.currentUser = currentUser;
    this.storage = storage;
    this.subscriptionAccess = subscriptionAccess;
  }

  @Transactional
  public UserProfile create(CreateUserProfileCommand command) {
    UUID organizationId = TenantContext.require();
    subscriptionAccess.assertCanAddUser(organizationId);
    String subject = command.keycloakSubject().trim();
    if (users.existsByOrganizationIdAndKeycloakSubject(organizationId, subject)) {
      throw new ConflictException("A user with subject '%s' already exists".formatted(subject));
    }
    requireDepartment(organizationId, command.departmentId());
    validateManager(organizationId, null, command.managerId());

    UserProfile user =
        new UserProfile(
            organizationId,
            subject,
            command.email(),
            command.displayName(),
            command.departmentId());
    if (command.approvalLimitAmount() != null) {
      user.setApprovalLimit(command.approvalLimitAmount(), command.approvalLimitCurrency());
    }
    user.assignManager(command.managerId());
    return users.save(user);
  }

  @Transactional
  public UserProfile update(UUID id, UpdateUserProfileCommand command) {
    UUID organizationId = TenantContext.require();
    UserProfile user = get(id);
    requireDepartment(organizationId, command.departmentId());
    validateManager(organizationId, id, command.managerId());

    user.updateDetails(command.displayName(), command.departmentId());
    user.assignManager(command.managerId());
    user.setApprovalLimit(command.approvalLimitAmount(), command.approvalLimitCurrency());
    user.setActive(command.active());
    return users.save(user);
  }

  @Transactional
  public void delete(UUID id) {
    UserProfile user = get(id);
    user.softDelete(clock.instant(), actorProvider.currentActor());
    users.save(user);
  }

  @Transactional
  public UserProfile saveCurrent(SelfProfileCommand command) {
    UUID organizationId = TenantContext.require();
    requireDepartment(organizationId, command.departmentId());
    UserProfile user =
        users
            .findByOrganizationIdAndKeycloakSubject(organizationId, currentUser.subject())
            .orElseGet(
                () ->
                    new UserProfile(
                        organizationId,
                        currentUser.subject(),
                        currentUser.email(),
                        command.displayName(),
                        command.departmentId()));
    validateManager(organizationId, user.getId(), command.managerId());
    user.updateDetails(command.displayName(), command.departmentId());
    user.assignManager(command.managerId());
    user.setActive(true);
    return users.save(user);
  }

  @Transactional
  public UserProfile uploadAvatar(byte[] content, String contentType) {
    if (content == null || content.length == 0) {
      throw new BusinessRuleException("Avatar file is required");
    }
    if (content.length > MAX_AVATAR_BYTES) {
      throw new BusinessRuleException("Avatar must be 5MB or smaller");
    }
    String normalizedType = contentType == null ? "" : contentType.toLowerCase();
    if (!AVATAR_TYPES.contains(normalizedType)) {
      throw new BusinessRuleException("Avatar must be a JPEG, PNG or WebP image");
    }
    UUID organizationId = TenantContext.require();
    UserProfile user =
        users
            .findByOrganizationIdAndKeycloakSubject(organizationId, currentUser.subject())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Create a user profile before uploading an avatar"));
    String previousKey = user.getAvatarObjectKey();
    String extension =
        normalizedType.equals("image/png")
            ? "png"
            : normalizedType.equals("image/webp") ? "webp" : "jpg";
    String key =
        "avatars/%s/%s/%s.%s".formatted(organizationId, user.getId(), UUID.randomUUID(), extension);
    storage.put(key, content, normalizedType);
    user.setAvatar(key, normalizedType);
    UserProfile saved = users.save(user);
    if (previousKey != null) {
      try {
        storage.delete(previousKey);
      } catch (RuntimeException ignored) {
        // New avatar is stored; cleanup can be retried manually.
      }
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public Avatar currentAvatar() {
    UUID organizationId = TenantContext.require();
    UserProfile user =
        users
            .findByOrganizationIdAndKeycloakSubject(organizationId, currentUser.subject())
            .orElseThrow(
                () -> new ResourceNotFoundException("No avatar is available for this user"));
    if (user.getAvatarObjectKey() == null) {
      throw new ResourceNotFoundException("No avatar is available for this user");
    }
    try (var input = storage.get(user.getAvatarObjectKey())) {
      return new Avatar(input.readAllBytes(), user.getAvatarContentType());
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Could not read avatar", ex);
    }
  }

  @Transactional
  public void removeAvatar() {
    UUID organizationId = TenantContext.require();
    UserProfile user =
        users
            .findByOrganizationIdAndKeycloakSubject(organizationId, currentUser.subject())
            .orElseThrow(
                () -> new ResourceNotFoundException("No avatar is available for this user"));
    String key = user.getAvatarObjectKey();
    user.clearAvatar();
    users.save(user);
    if (key != null) {
      storage.delete(key);
    }
  }

  @Transactional
  public UserProfile getCurrent() {
    UUID organizationId = TenantContext.require();
    UserProfile existing =
        users
            .findByOrganizationIdAndKeycloakSubject(organizationId, currentActorSubject())
            .orElse(null);
    if (existing != null) {
      return existing;
    }
    String defaultDepartment =
        currentUser.hasRole("PLATFORM_ADMIN") ? "Platform administration" : "General";
    UUID departmentId =
        departments.search(organizationId, null, PageRequest.of(0, 1)).stream()
            .findFirst()
            .map(UserProfileServiceSupport::departmentId)
            .orElseGet(
                () ->
                    departments
                        .save(
                            new com.company.procurement.organization.domain.Department(
                                organizationId, defaultDepartment))
                        .getId());
    UserProfile profile =
        new UserProfile(
            organizationId,
            currentUser.subject(),
            currentUser.email(),
            currentUser.displayName(),
            departmentId);
    return users.save(profile);
  }

  @Transactional(readOnly = true)
  public java.util.List<UserProfile> delegationCandidates() {
    UUID organizationId = TenantContext.require();
    return users.search(organizationId, null, true, null, PageRequest.of(0, 100)).getContent();
  }

  @Transactional(readOnly = true)
  public UserProfile get(UUID id) {
    UUID organizationId = TenantContext.require();
    return users
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("User %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<UserProfile> search(
      UUID departmentId, Boolean active, String text, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    return users.search(organizationId, departmentId, active, text, pageable);
  }

  private String currentActorSubject() {
    return currentUser.subject();
  }

  public record Avatar(byte[] content, String contentType) {}

  private void requireDepartment(UUID organizationId, UUID departmentId) {
    departments
        .findByIdAndOrganizationId(departmentId, organizationId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "Department %s does not exist in this organization".formatted(departmentId)));
  }

  private void validateManager(UUID organizationId, UUID userId, UUID managerId) {
    if (managerId == null) {
      return;
    }
    if (managerId.equals(userId)) {
      throw new BusinessRuleException("A user cannot be their own manager");
    }
    UUID cursor = managerId;
    int hops = 0;
    while (cursor != null) {
      if (cursor.equals(userId)) {
        throw new BusinessRuleException("Manager assignment would create a cycle");
      }
      if (++hops > MAX_MANAGER_CHAIN) {
        throw new BusinessRuleException("Manager chain is too deep");
      }
      UUID current = cursor;
      UserProfile manager =
          users
              .findByIdAndOrganizationId(current, organizationId)
              .orElseThrow(
                  () ->
                      new BusinessRuleException(
                          "Manager %s does not exist in this organization".formatted(current)));
      cursor = manager.getManagerId();
    }
  }
}

final class UserProfileServiceSupport {
  private UserProfileServiceSupport() {}

  static UUID departmentId(com.company.procurement.organization.domain.Department department) {
    return department.getId();
  }
}
