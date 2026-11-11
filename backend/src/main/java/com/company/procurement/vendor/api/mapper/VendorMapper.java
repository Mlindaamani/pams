package com.company.procurement.vendor.api.mapper;

import com.company.procurement.vendor.api.dto.VendorResponse;
import com.company.procurement.vendor.domain.Vendor;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VendorMapper {

  VendorResponse toResponse(Vendor vendor);
}
