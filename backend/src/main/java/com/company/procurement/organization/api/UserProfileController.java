package com.company.procurement.organization.api;

import com.company.procurement.organization.api.dto.CreateUserProfileRequest;
import com.company.procurement.organization.api.dto.InviteUserRequest;
import com.company.procurement.organization.api.dto.SelfProfileRequest;
import com.company.procurement.organization.api.dto.UpdateUserProfileRequest;
import com.company.procurement.organization.api.dto.UserOptionResponse;
import com.company.procurement.organization.api.dto.UserProfileResponse;
import com.company.procurement.organization.api.mapper.UserProfileMapper;
import com.company.procurement.organization.application.CreateUserProfileCommand;
import com.company.procurement.organization.application.SelfProfileCommand;
import com.company.procurement.organization.application.UpdateUserProfileCommand;
import com.company.procurement.organization.application.UserIdentitySyncService;
import com.company.procurement.organization.application.UserInvitationService;
import com.company.procurement.organization.application.UserProfileService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.shared.api.MoneyRequest;
import com.company.procurement.shared.api.PageResponse;
import com.company.procurement.shared.web.PageRequests;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Tag(name = "Users", description = "User profiles, departments, managers and approval limits")
@RequestMapping("/api/v1/users")
public class UserProfileController {

  private static final Set<String> SORTABLE = Set.of("createdAt", "email", "displayName");

  private final UserProfileService users;
  private final UserIdentitySyncService identitySync;
  private final UserInvitationService invitations;
  private final UserProfileMapper mapper;

  public UserProfileController(
      UserProfileService users,
      UserIdentitySyncService identitySync,
      UserInvitationService invitations,
      UserProfileMapper mapper) {
    this.users = users;
    this.identitySync = identitySync;
    this.invitations = invitations;
    this.mapper = mapper;
  }

  @PreAuthorize("@permissions.has('user:manage')")
  @PostMapping
  public ResponseEntity<UserProfileResponse> create(
      @Valid @RequestBody CreateUserProfileRequest request) {
    UserProfile created =
        users.create(
            new CreateUserProfileCommand(
                request.keycloakSubject(),
                request.email(),
                request.displayName(),
                request.departmentId(),
                request.managerId(),
                amount(request.approvalLimit()),
                currency(request.approvalLimit())));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
    return ResponseEntity.created(location).body(mapper.toResponse(created));
  }

  @PostMapping("/invitations")
  @PreAuthorize("@permissions.has('user:manage')")
  public ResponseEntity<UserProfileResponse> invite(@Valid @RequestBody InviteUserRequest request) {
    UserProfile invited =
        invitations.invite(
            new UserInvitationService.InviteUserCommand(
                request.email(),
                request.displayName(),
                request.departmentId(),
                request.managerId(),
                request.roles(),
                amount(request.approvalLimit()),
                currency(request.approvalLimit())));
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(mapper.toResponse(invited));
  }

  @GetMapping("/me")
  public UserProfileResponse me() {
    identitySync.syncCurrentIdentity();
    return mapper.toResponse(users.getCurrent());
  }

  @PutMapping("/me")
  public UserProfileResponse saveMe(@Valid @RequestBody SelfProfileRequest request) {
    identitySync.pushDisplayName(request.displayName());
    return mapper.toResponse(
        users.saveCurrent(
            new SelfProfileCommand(
                request.displayName(), request.departmentId(), request.managerId())));
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public UserProfileResponse uploadAvatar(@RequestPart("file") MultipartFile file)
      throws java.io.IOException {
    return mapper.toResponse(users.uploadAvatar(file.getBytes(), file.getContentType()));
  }

  @GetMapping("/me/avatar")
  public ResponseEntity<byte[]> avatar() {
    UserProfileService.Avatar avatar = users.currentAvatar();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(avatar.contentType()))
        .header("Cache-Control", "no-store")
        .body(avatar.content());
  }

  @DeleteMapping("/me/avatar")
  public ResponseEntity<Void> removeAvatar() {
    users.removeAvatar();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/delegation-candidates")
  public java.util.List<UserOptionResponse> delegationCandidates() {
    return users.delegationCandidates().stream()
        .map(
            user ->
                new UserOptionResponse(user.getId(), user.getDisplayName(), user.getDepartmentId()))
        .toList();
  }

  @PreAuthorize("@permissions.has('user:read')")
  @GetMapping("/{id}")
  public UserProfileResponse get(@PathVariable UUID id) {
    return mapper.toResponse(users.get(id));
  }

  @PreAuthorize("@permissions.has('user:read')")
  @GetMapping
  public PageResponse<UserProfileResponse> list(
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    Pageable pageable = PageRequests.of(page, size, sort, SORTABLE);
    return PageResponse.from(users.search(departmentId, active, q, pageable), mapper::toResponse);
  }

  @PreAuthorize("@permissions.has('user:manage')")
  @PutMapping("/{id}")
  public UserProfileResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateUserProfileRequest request) {
    return mapper.toResponse(
        users.update(
            id,
            new UpdateUserProfileCommand(
                request.displayName(),
                request.departmentId(),
                request.managerId(),
                amount(request.approvalLimit()),
                currency(request.approvalLimit()),
                request.active())));
  }

  @PreAuthorize("@permissions.has('user:manage')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    users.delete(id);
    return ResponseEntity.noContent().build();
  }

  private static BigDecimal amount(MoneyRequest money) {
    return money == null ? null : money.toAmount();
  }

  private static String currency(MoneyRequest money) {
    return money == null ? null : money.currency();
  }
}
