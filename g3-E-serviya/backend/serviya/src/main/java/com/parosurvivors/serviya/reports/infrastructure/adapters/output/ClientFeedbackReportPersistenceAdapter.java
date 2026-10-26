package com.parosurvivors.serviya.reports.infrastructure.adapters.output;

import com.parosurvivors.serviya.reports.application.ports.output.ClientFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.ClientFeedbackReportEntity;
import com.parosurvivors.serviya.reports.infrastructure.mappers.ClientFeedbackReportPersistenceMapper;
import com.parosurvivors.serviya.reports.infrastructure.repositories.ClientFeedbackReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientFeedbackReportPersistenceAdapter implements ClientFeedbackReportPersistencePort {

    private final ClientFeedbackReportRepository repository;
    private final ClientFeedbackReportPersistenceMapper mapper;

    @Override
    public ClientFeedbackReport save(ClientFeedbackReport report) {
        ClientFeedbackReportEntity saved = repository.save(mapper.toEntity(report));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ClientFeedbackReport> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ClientFeedbackReport> findByReportId(Long reportId) {
        return repository.findByReportId(reportId).map(mapper::toDomain);
    }
}
