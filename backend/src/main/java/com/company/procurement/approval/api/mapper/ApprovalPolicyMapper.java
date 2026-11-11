package com.company.procurement.approval.api.mapper;

import com.company.procurement.approval.api.dto.ApprovalPolicyResponse;
import com.company.procurement.approval.api.dto.PolicyStepResponse;
import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApprovalPolicyStep;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApprovalPolicyMapper {

  ApprovalPolicyResponse toResponse(ApprovalPolicy policy);

  PolicyStepResponse toStepResponse(ApprovalPolicyStep step);
}
