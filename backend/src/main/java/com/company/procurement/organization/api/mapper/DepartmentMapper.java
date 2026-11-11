package com.company.procurement.organization.api.mapper;

import com.company.procurement.organization.api.dto.DepartmentResponse;
import com.company.procurement.organization.domain.Department;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

  DepartmentResponse toResponse(Department department);
}
