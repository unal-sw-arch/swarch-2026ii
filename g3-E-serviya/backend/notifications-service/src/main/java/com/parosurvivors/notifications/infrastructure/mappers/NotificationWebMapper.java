package com.parosurvivors.notifications.infrastructure.mappers;

import com.parosurvivors.notifications.application.dto.result.NotificationDeliveryResult;
import com.parosurvivors.notifications.domain.NotificationChannel;
import com.parosurvivors.notifications.infrastructure.dto.response.NotificationChannelResponse;
import com.parosurvivors.notifications.infrastructure.dto.response.NotificationDeliveryResponse;
import org.mapstruct.Mapper;

import java.util.List;

/** Mapper web (MapStruct) de notificaciones: Result/dominio->Response (solo lecturas). */
@Mapper(componentModel = "spring")
public interface NotificationWebMapper {

    NotificationDeliveryResponse toResponse(NotificationDeliveryResult result);

    NotificationChannelResponse toResponse(NotificationChannel channel);

    List<NotificationChannelResponse> toChannelResponses(List<NotificationChannel> channels);
}
