package com.company.procurement.request.api.mapper;

import com.company.procurement.request.api.dto.AttachmentResponse;
import com.company.procurement.request.domain.RequestAttachment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {

  AttachmentResponse toResponse(RequestAttachment attachment);
}
