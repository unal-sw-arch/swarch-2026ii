package com.parosurvivors.serviya.services.application.services;

import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.query.SearchServiceQuery;
import com.parosurvivors.serviya.services.application.mappers.ServiceCommandMapper;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Implementacion del marketplace de servicios. Solo conoce su propio dominio/persistencia; las
 * vistas compuestas y la resolucion de filtros de otros modulos viven en el BFF.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceService implements MarketplaceServicePort {

    private final ServicePersistencePort persistencePort;
    private final ServiceCommandMapper commandMapper;

    @Override
    public Service create(CreateServiceCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Service service = commandMapper.toDomain(command);
        service.setCreatedAt(now);
        service.setUpdatedAt(now);
        return persistencePort.save(service);
    }

    @Override
    public Optional<Service> getById(Long id) {
        return persistencePort.findById(id)
                .filter(s -> !s.isDeleted());
    }

    @Override
    public List<Service> getAll() {
        return persistencePort.findAll().stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.toList());
    }

    @Override
    public Page<Service> search(SearchServiceQuery criteria, Pageable pageable) {
        return persistencePort.search(criteria, pageable);
    }

    @Override
    public Service update(UpdateServiceCommand command, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(command.serviceId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Servicio no encontrado con id: " + command.serviceId()));

        requireOwnership(service, requesterId, isAdmin);

        // PATCH semantico: el mapper aplica solo los campos no-nulos del command
        // (IGNORE strategy).
        commandMapper.updateFromCommand(command, service);
        service.setUpdatedAt(LocalDateTime.now());

        return persistencePort.update(service);
    }

    @Override
    public void delete(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));
        requireOwnership(service, requesterId, isAdmin);
        persistencePort.deleteById(id);
    }

    @Override
    public void softDelete(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.softDelete();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    @Override
    public void activate(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.activate();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    @Override
    public void deactivate(Long id, Long requesterId, boolean isAdmin) {
        Service service = persistencePort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con id: " + id));

        requireOwnership(service, requesterId, isAdmin);
        service.deactivate();
        service.setUpdatedAt(LocalDateTime.now());
        persistencePort.update(service);
    }

    /**
     * El actor debe ser el oferente propietario del servicio, o un admin (IDOR: B1).
     * Mismo patron "participante O admin" que {@code ServiceRequestQueryService.getRequestHistory}.
     */
    private void requireOwnership(Service service, Long requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (requesterId == null || !requesterId.equals(service.getOffererId())) {
            throw new ForbiddenException("El usuario no es el propietario del servicio");
        }
    }

    @Override
    public void deactivateAllByOfferer(Long offererId) {
        // Usado por UserDeletionService al eliminar un oferente: oculta todos sus servicios del buscador.
        // Corre dentro de la transaccion del orquestador llamante.
        LocalDateTime now = LocalDateTime.now();
        for (Service service : persistencePort.findByOffererId(offererId)) {
            if (service.isAvailable()) {
                service.deactivate();
                service.setUpdatedAt(now);
                persistencePort.update(service);
            }
        }
    }
}
