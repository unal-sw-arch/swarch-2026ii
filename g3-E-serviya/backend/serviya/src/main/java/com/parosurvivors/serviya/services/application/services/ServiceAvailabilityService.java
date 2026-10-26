package com.parosurvivors.serviya.services.application.services;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.parosurvivors.serviya.profiles.application.ports.input.OffererAvailabilityServicePort;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceAvailabilityCommand;
import com.parosurvivors.serviya.services.application.ports.input.ServiceAvailabilityServicePort;
import com.parosurvivors.serviya.services.application.ports.output.ServiceAvailabilityPersistencePort;
import com.parosurvivors.serviya.services.domain.ServiceAvailability;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ServiceAvailabilityService implements ServiceAvailabilityServicePort {

    private final ServiceAvailabilityPersistencePort persistencePort;
    private final OffererAvailabilityServicePort offererAvailabilityService;

    @Override
    public ServiceAvailability create(CreateServiceAvailabilityCommand command) {
        validate(command);
        ServiceAvailability availability = ServiceAvailability.builder()
                .serviceId(command.serviceId())
                .weekDay(command.weekDay())
                .startTime(command.startTime())
                .endTime(command.endTime())
                .isActive(command.isActive())
                .build();
        return persistencePort.save(availability);
    }

    @Override
    public void delete(Long id) {
        persistencePort.deleteById(id);
    }

    @Override
    public ServiceAvailability update(UpdateServiceAvailabilityCommand command) {
        ServiceAvailability existing = persistencePort.findById(command.id());

        validateForUpdate(command, existing);
        existing.setWeekDay(command.weekDay());
        existing.setStartTime(command.startTime());
        existing.setEndTime(command.endTime());
        existing.setActive(command.isActive());
        return persistencePort.update(existing);
    }

    @Override
    public List<ServiceAvailability> getByServiceId(long serviceId) {
        return persistencePort.findByServiceId(serviceId).stream()
                .filter(ServiceAvailability::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public void applyGeneralTemplate(Long serviceId, Long offererId) {
        List<ServiceAvailability> existingSlots = persistencePort.findByServiceId(serviceId);
        existingSlots.forEach(slot -> persistencePort.deleteById(slot.getId()));

        List<OffererAvailability> generalSlots = offererAvailabilityService.getSchedule(offererId).stream()
                .filter(OffererAvailability::isActive)
                .toList();

        for (OffererAvailability slot : generalSlots) {
            create(new CreateServiceAvailabilityCommand(
                    serviceId,
                    slot.getWeekDay().byteValue(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    true));
        }
    }

    private void validate(CreateServiceAvailabilityCommand command) {
        if (command.serviceId() == null) {
            throw new IllegalArgumentException("serviceId es requerido");
        }
        if (command.weekDay() < 0 || command.weekDay() > 6) {
            throw new IllegalArgumentException("weekDay inválido");
        }
        if (command.startTime() == null || command.endTime() == null || !command.startTime().isBefore(command.endTime())) {
            throw new IllegalArgumentException("Rango de horario inválido");
        }
    }

    private void validateForUpdate(UpdateServiceAvailabilityCommand command, ServiceAvailability existing) {
        if (existing == null) {
            throw new IllegalArgumentException("Disponibilidad no encontrada");
        }
        if (command.weekDay() < 0 || command.weekDay() > 6) {
            throw new IllegalArgumentException("weekDay inválido");
        }
        if (command.startTime() == null || command.endTime() == null || !command.startTime().isBefore(command.endTime())) {
            throw new IllegalArgumentException("Rango de horario inválido");
        }
    }
}
