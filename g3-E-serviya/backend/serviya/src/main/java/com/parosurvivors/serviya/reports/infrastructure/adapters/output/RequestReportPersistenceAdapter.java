package com.parosurvivors.serviya.reports.infrastructure.adapters.output;

import com.parosurvivors.serviya.reports.application.ports.output.RequestReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.RequestReportEntity;
import com.parosurvivors.serviya.reports.infrastructure.mappers.RequestReportPersistenceMapper;
import com.parosurvivors.serviya.reports.infrastructure.repositories.RequestReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RequestReportPersistenceAdapter implements RequestReportPersistencePort {

    private final RequestReportRepository repository;
    private final RequestReportPersistenceMapper mapper;

    @Override
    public RequestReport save(RequestReport report) {
        RequestReportEntity saved = repository.save(mapper.toEntity(report));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RequestReport> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RequestReport> findByReportId(Long reportId) {
        return repository.findByReportId(reportId).map(mapper::toDomain);
    }
}
