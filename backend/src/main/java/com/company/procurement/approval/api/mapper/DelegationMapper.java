package com.company.procurement.approval.api.mapper;

import com.company.procurement.approval.api.dto.DelegationResponse;
import com.company.procurement.approval.domain.Delegation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DelegationMapper {

  DelegationResponse toResponse(Delegation delegation);
}
