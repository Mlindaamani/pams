package com.company.procurement.organization.api.mapper;

import com.company.procurement.organization.api.dto.OrganizationResponse;
import com.company.procurement.organization.domain.Organization;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

  OrganizationResponse toResponse(Organization organization);
}
