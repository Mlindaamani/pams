package com.company.procurement.organization.api.mapper;

import com.company.procurement.organization.api.dto.UserProfileResponse;
import com.company.procurement.organization.domain.UserProfile;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

  @Mapping(
      target = "approvalLimit",
      expression =
          "java(MoneyDto.of(userProfile.getApprovalLimitAmount(),"
              + " userProfile.getApprovalLimitCurrency()))")
  @Mapping(target = "hasAvatar", expression = "java(userProfile.getAvatarObjectKey() != null)")
  @Mapping(target = "organizationId", source = "userProfile.organizationId")
  @Mapping(target = "organizationName", source = "organizationName")
  @Mapping(target = "roles", source = "roles")
  UserProfileResponse toResponse(
      UserProfile userProfile, String organizationName, Set<String> roles);

  default UserProfileResponse toResponse(UserProfile userProfile, String organizationName) {
    return toResponse(userProfile, organizationName, Set.of());
  }

  default UserProfileResponse toResponse(UserProfile userProfile) {
    return toResponse(userProfile, null, Set.of());
  }
}
