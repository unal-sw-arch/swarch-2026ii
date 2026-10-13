package com.parosurvivors.notifications.infrastructure.mappers;

import com.parosurvivors.notifications.domain.NotificationDelivery;
import com.parosurvivors.notifications.infrastructure.entities.NotificationDeliveryEntity;
import org.mapstruct.Mapper;

/**
 * La {@code version} viaja en ambos sentidos A PROPÓSITO. En el monolito el dominio no la tenía: al
 * actualizar, la entidad salía con {@code version = null}, Spring Data la trataba como NUEVA ({@code isNew}
 * mira la versión cuando existe) y hacía {@code persist} de una fila con id existente → excepción en
 * markAsRead, el envío diferido y el reintento. Conservarla hace que el update sea un {@code merge}
 * con verificación optimista (B10).
 */
@Mapper(componentModel = "spring")
public interface NotificationDeliveryPersistenceMapper {

    NotificationDelivery toDomain(NotificationDeliveryEntity entity);

    NotificationDeliveryEntity toEntity(NotificationDelivery domain);
}
