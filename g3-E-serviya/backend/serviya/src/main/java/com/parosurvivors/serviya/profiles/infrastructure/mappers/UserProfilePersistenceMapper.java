package com.parosurvivors.serviya.profiles.infrastructure.mappers;

import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.profiles.infrastructure.entities.UserProfileEntity;
import org.mapstruct.Mapper;

/**
 * Conversión dominio &lt;-&gt; entidad para {@link UserProfile}. {@code documentNumber} y
 * {@code phoneNumber} se mapean como String en claro; el cifrado/descifrado AES-256-GCM lo
 * aplica {@code PiiAttributeConverter} en la frontera con la BD (VARBINARY).
 */
@Mapper(componentModel = "spring")
public interface UserProfilePersistenceMapper {

    UserProfile toDomain(UserProfileEntity entity);

    UserProfileEntity toEntity(UserProfile domain);
}
