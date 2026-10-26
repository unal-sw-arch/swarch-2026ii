package com.parosurvivors.notifications.infrastructure.mappers;

import com.parosurvivors.notifications.domain.Notification;
import com.parosurvivors.notifications.infrastructure.entities.NotificationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationPersistenceMapper {

    Notification toDomain(NotificationEntity entity);

    /** Las notificaciones solo se insertan (nunca se actualizan): la versión arranca en null → INSERT. */
    @Mapping(target = "version", ignore = true)
    NotificationEntity toEntity(Notification domain);
}
