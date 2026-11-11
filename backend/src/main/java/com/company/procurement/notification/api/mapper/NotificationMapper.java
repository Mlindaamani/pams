package com.company.procurement.notification.api.mapper;

import com.company.procurement.notification.api.dto.NotificationResponse;
import com.company.procurement.notification.domain.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

  NotificationResponse toResponse(Notification notification);
}
