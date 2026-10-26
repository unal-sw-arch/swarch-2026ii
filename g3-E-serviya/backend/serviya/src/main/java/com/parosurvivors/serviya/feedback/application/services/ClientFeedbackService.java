package com.parosurvivors.serviya.feedback.application.services;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitClientFeedbackCommand;
import com.parosurvivors.serviya.feedback.application.dto.result.ClientFeedbackResult;
import com.parosurvivors.serviya.feedback.application.mappers.ClientFeedbackCommandMapper;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.TagSentiment;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.TagRef;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fachada del feedback del oferente a un cliente (rating + reseña unificados en {@link ClientFeedback}).
 * Simétrico a {@code ServiceFeedbackService}: {@code submit}/{@code revert} persisten y publican
 * {@link ClientFeedbackSubmittedEvent}/{@link ClientFeedbackRevertedEvent} autocontenidos para las
 * métricas del cliente. Ver documents/project-structure/estructura-servicios.docx (módulo 5).
 */
@Component
@RequiredArgsConstructor
public class ClientFeedbackService implements ClientFeedbackServicePort {

    private final ClientFeedbackPersistencePort clientFeedbackPersistencePort;
    private final ClientFeedbackTagPersistencePort clientFeedbackTagPersistencePort;
    private final ClientFeedbackTagCatalogPersistencePort clientFeedbackTagCatalogPersistencePort;
    private final ServiceRequestReadPort serviceRequestReadPort;
    private final ClientFeedbackCommandMapper commandMapper;
    private final DomainEventPublisherPort eventPublisher;

    @Override
    @Transactional
    public void submitClientFeedback(SubmitClientFeedbackCommand command) {
        ServiceRequest request = serviceRequestReadPort.findById(command.requestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solicitud no encontrada: " + command.requestId()));
        if (!request.getOffererId().equals(command.offererId())) {
            throw new UnauthorizedException("El feedback al cliente solo lo puede dejar el oferente de la solicitud");
        }
        if (!(request.isPresumablyCompleted() || request.isCompleted())) {
            throw new InvalidStateException(
                    "Solo se puede calificar una solicitud prestada; estado actual: " + request.getStatus());
        }
        if (clientFeedbackPersistencePort.findByRequestId(command.requestId()).isPresent()) {
            throw new InvalidStateException(
                    "Ya existe feedback de cliente para la solicitud " + command.requestId());
        }

        ClientFeedback feedback = commandMapper.toFeedback(command);
        feedback.setCreatedAt(LocalDateTime.now());
        ClientFeedback saved = clientFeedbackPersistencePort.save(feedback);

        List<Long> tagIds = command.tagIds() == null ? List.of() : command.tagIds();
        if (!tagIds.isEmpty()) {
            clientFeedbackTagPersistencePort.addTags(saved.getId(), tagIds);
        }

        eventPublisher.publish(new ClientFeedbackSubmittedEvent(
                request.getId(),
                saved.getClientId(),
                saved.getOffererId(),
                saved.getRating(),
                saved.hasComment(),
                resolveTagRefs(tagIds)));
    }

    @Override
    @Transactional
    public boolean revertFeedbackById(Long feedbackId) {
        Optional<ClientFeedback> existing = clientFeedbackPersistencePort.findById(feedbackId);
        if (existing.isEmpty()) {
            return false;
        }
        ClientFeedback feedback = existing.get();
        Long requestId = feedback.getRequestId();
        List<Long> tagIds = clientFeedbackTagPersistencePort.findTagIdsByFeedbackId(feedback.getId());
        clientFeedbackTagPersistencePort.deleteByFeedbackId(feedback.getId());
        clientFeedbackPersistencePort.deleteById(feedback.getId());

        eventPublisher.publish(new ClientFeedbackRevertedEvent(
                requestId,
                feedback.getClientId(),
                feedback.getOffererId(),
                feedback.getRating(),
                feedback.hasComment(),
                resolveTagRefs(tagIds)));
        return true;
    }

    @Override
    public ClientFeedbackResult getClientFeedback(Long requestId) {
        ClientFeedback feedback = clientFeedbackPersistencePort.findByRequestId(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feedback de cliente no encontrado para la solicitud: " + requestId));
        return toResult(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForRequest(Long requestId) {
        return clientFeedbackPersistencePort.existsByRequestId(requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientFeedbackResult> getClientFeedbackById(Long feedbackId) {
        return clientFeedbackPersistencePort.findById(feedbackId).map(f -> new ClientFeedbackResult(
                f.getRequestId(),
                f.getClientId(),
                f.getOffererId(),
                f.getRating(),
                f.getComment(),
                resolveTagNames(clientFeedbackTagPersistencePort.findTagIdsByFeedbackId(f.getId())),
                f.getCreatedAt()));
    }

    @Override
    public Page<ClientFeedbackResult> getClientFeedbackList(Long clientId, Pageable pageable) {
        return clientFeedbackPersistencePort.findByClientId(clientId, pageable)
                .map(this::toResult);
    }

    @Override
    public Page<ClientFeedbackResult> getClientFeedbackByOfferer(Long offererId, Pageable pageable) {
        return clientFeedbackPersistencePort.findByOffererId(offererId, pageable)
                .map(this::toResult);
    }

    /** Arma el Result de una entrada, resolviendo sus tagIds a nombres de tag desde el catalogo. */
    private ClientFeedbackResult toResult(ClientFeedback feedback) {
        List<Long> tagIds = clientFeedbackTagPersistencePort.findTagIdsByFeedbackId(feedback.getId());
        return new ClientFeedbackResult(
                feedback.getRequestId(),
                feedback.getClientId(),
                feedback.getOffererId(),
                feedback.getRating(),
                feedback.getComment(),
                resolveTagNames(tagIds),
                feedback.getCreatedAt());
    }

    /** Empareja cada tagId con su nombre del catalogo, para la vista de lectura (Result). */
    private List<String> resolveTagNames(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameByTag = clientFeedbackTagCatalogPersistencePort.findAll().stream()
                .collect(Collectors.toMap(ClientFeedbackTagCatalog::getId, ClientFeedbackTagCatalog::getTagName));
        return tagIds.stream()
                .map(nameByTag::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Empareja cada tagId con su sentimiento (P/N) del catálogo para armar el payload autocontenido. */
    private List<TagRef> resolveTagRefs(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TagSentiment> sentimentByTag = clientFeedbackTagCatalogPersistencePort.findAll().stream()
                .collect(Collectors.toMap(ClientFeedbackTagCatalog::getId, ClientFeedbackTagCatalog::getSentiment));
        return tagIds.stream()
                .map(tagId -> new TagRef(tagId, sentimentByTag.get(tagId)))
                .toList();
    }
}