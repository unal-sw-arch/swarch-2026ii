package com.parosurvivors.serviya.reports.infrastructure.adapters.output;

import com.parosurvivors.serviya.reports.application.ports.output.ServiceFeedbackReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.ServiceFeedbackReportEntity;
import com.parosurvivors.serviya.reports.infrastructure.mappers.ServiceFeedbackReportPersistenceMapper;
import com.parosurvivors.serviya.reports.infrastructure.repositories.ServiceFeedbackReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ServiceFeedbackReportPersistenceAdapter implements ServiceFeedbackReportPersistencePort {

    private final ServiceFeedbackReportRepository repository;
    private final ServiceFeedbackReportPersistenceMapper mapper;

    @Override
    public ServiceFeedbackReport save(ServiceFeedbackReport report) {
        ServiceFeedbackReportEntity saved = repository.save(mapper.toEntity(report));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ServiceFeedbackReport> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ServiceFeedbackReport> findByReportId(Long reportId) {
        return repository.findByReportId(reportId).map(mapper::toDomain);
    }
}
