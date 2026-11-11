package com.company.procurement.organization.api.mapper;

import com.company.procurement.organization.api.dto.UserProfileResponse;
import com.company.procurement.organization.domain.UserProfile;
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
  UserProfileResponse toResponse(UserProfile userProfile);
}
