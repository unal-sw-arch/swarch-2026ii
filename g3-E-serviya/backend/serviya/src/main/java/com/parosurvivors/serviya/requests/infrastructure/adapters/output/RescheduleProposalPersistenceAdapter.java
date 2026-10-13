package com.parosurvivors.serviya.requests.infrastructure.adapters.output;

import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalPersistencePort;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.infrastructure.entities.RescheduleProposalEntity;
import com.parosurvivors.serviya.requests.infrastructure.mappers.RescheduleProposalPersistenceMapper;
import com.parosurvivors.serviya.requests.infrastructure.repositories.RescheduleProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter de PERSISTENCIA (mutaciones) de propuestas de reprogramacion. Solo save/update; las lecturas
 * las sirve {@link RescheduleProposalReadAdapter} (RescheduleProposalReadPort).
 */
@Component
@RequiredArgsConstructor
public class RescheduleProposalPersistenceAdapter implements RescheduleProposalPersistencePort {

    private final RescheduleProposalRepository repository;
    private final RescheduleProposalPersistenceMapper mapper;

    @Override
    public RescheduleProposal save(RescheduleProposal proposal) {
        RescheduleProposalEntity saved = repository.save(mapper.toEntity(proposal));
        return mapper.toDomain(saved);
    }

    @Override
    public RescheduleProposal update(RescheduleProposal proposal) {
        RescheduleProposalEntity updated = repository.save(mapper.toEntity(proposal));
        return mapper.toDomain(updated);
    }
}
