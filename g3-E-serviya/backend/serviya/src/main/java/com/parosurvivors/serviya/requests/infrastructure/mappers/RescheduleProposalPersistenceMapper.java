package com.parosurvivors.serviya.requests.infrastructure.mappers;

import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.infrastructure.entities.RescheduleProposalEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RescheduleProposalPersistenceMapper {

    RescheduleProposal toDomain(RescheduleProposalEntity entity);

    RescheduleProposalEntity toEntity(RescheduleProposal domain);
}
