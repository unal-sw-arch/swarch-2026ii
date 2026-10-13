package com.parosurvivors.notifications.infrastructure.mappers;

import com.parosurvivors.notifications.domain.NotificationChannel;
import com.parosurvivors.notifications.infrastructure.entities.NotificationChannelEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationChannelPersistenceMapper {

    NotificationChannel toDomain(NotificationChannelEntity entity);
}
