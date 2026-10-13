package com.parosurvivors.serviya.reports.infrastructure.adapters.output;

import com.parosurvivors.serviya.reports.application.ports.output.ReportActionPersistencePort;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.infrastructure.entities.ReportActionEntity;
import com.parosurvivors.serviya.reports.infrastructure.mappers.ReportActionPersistenceMapper;
import com.parosurvivors.serviya.reports.infrastructure.repositories.ReportActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportActionPersistenceAdapter implements ReportActionPersistencePort {

    private final ReportActionRepository repository;
    private final ReportActionPersistenceMapper mapper;

    @Override
    public ReportAction save(ReportAction action) {
        ReportActionEntity saved = repository.save(mapper.toEntity(action));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ReportAction> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ReportAction> findByReportId(Long reportId) {
        return repository.findByReportId(reportId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
