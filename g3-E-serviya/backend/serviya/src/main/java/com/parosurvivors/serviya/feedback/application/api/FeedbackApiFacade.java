package com.parosurvivors.serviya.feedback.application.api;

import com.parosurvivors.serviya.feedback.api.FeedbackCommandApi;
import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.ClientFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.TagCatalogEntry;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de feedback sobre los casos de uso existentes.
 *
 * <p>Las lecturas usan los puertos de persistencia del propio modulo para devolver los ids de los
 * tags; los nombres/sentimiento se exponen aparte en {@link TagCatalogEntry}. Las escrituras
 * delegan en los puertos de entrada.</p>
 */
@Component
@RequiredArgsConstructor
public class FeedbackApiFacade implements FeedbackQueryApi, FeedbackCommandApi {

    private final ServiceFeedbackPersistencePort serviceFeedbackPersistencePort;
    private final ServiceFeedbackTagPersistencePort serviceFeedbackTagPersistencePort;
    private final ServiceFeedbackTagCatalogPersistencePort serviceFeedbackTagCatalogPersistencePort;
    private final ClientFeedbackPersistencePort clientFeedbackPersistencePort;
    private final ClientFeedbackTagPersistencePort clientFeedbackTagPersistencePort;
    private final ClientFeedbackTagCatalogPersistencePort clientFeedbackTagCatalogPersistencePort;
    private final ServiceFeedbackServicePort serviceFeedbackServicePort;
    private final ClientFeedbackServicePort clientFeedbackServicePort;

    // ---------------------------------------------------------------------
    // FeedbackQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<ServiceFeedbackSnapshot> findServiceFeedback(Long feedbackId) {
        return serviceFeedbackPersistencePort.findById(feedbackId)
                .map(this::toServiceFeedbackSnapshot);
    }

    @Override
    public Optional<ClientFeedbackSnapshot> findClientFeedback(Long feedbackId) {
        return clientFeedbackPersistencePort.findById(feedbackId)
                .map(this::toClientFeedbackSnapshot);
    }

    @Override
    public List<ServiceFeedbackSnapshot> getRecentServiceFeedback(Long serviceId, int limit) {
        return serviceFeedbackPersistencePort.findRecentByServiceId(serviceId, limit).stream()
                .map(this::toServiceFeedbackSnapshot)
                .toList();
    }

    @Override
    public boolean existsServiceFeedbackForRequest(Long requestId) {
        return serviceFeedbackPersistencePort.existsByRequestId(requestId);
    }

    @Override
    public boolean existsClientFeedbackForRequest(Long requestId) {
        return clientFeedbackPersistencePort.existsByRequestId(requestId);
    }

    @Override
    public List<TagCatalogEntry> getServiceTags() {
        return serviceFeedbackTagCatalogPersistencePort.findAll().stream()
                .map(tag -> new TagCatalogEntry(tag.getId(), tag.getTagName(), tag.isPositive()))
                .toList();
    }

    @Override
    public List<TagCatalogEntry> getClientTags() {
        return clientFeedbackTagCatalogPersistencePort.findAll().stream()
                .map(tag -> new TagCatalogEntry(tag.getId(), tag.getTagName(), tag.isPositive()))
                .toList();
    }

    @Override
    public double averageServiceFeedbackRating() {
        Double average = serviceFeedbackPersistencePort.findAverageRating();
        return average == null || average.isNaN() ? 0.0 : average;
    }

    @Override
    public PageResult<ServiceFeedbackSnapshot> searchServiceFeedback(
            Long serviceId, Long clientId, String keyword, Integer ratingMin, Integer ratingMax,
            int page, int size) {
        if (serviceId == null && clientId == null) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        Page<ServiceFeedback> result = serviceId != null
                ? serviceFeedbackPersistencePort.findByServiceIdAndFilters(
                        serviceId, keyword, ratingMin, ratingMax, PageRequest.of(page, size))
                : serviceFeedbackPersistencePort.findByClientIdAndFilters(
                        clientId, keyword, ratingMin, ratingMax, PageRequest.of(page, size));
        return toPageResult(result, this::toServiceFeedbackSnapshot);
    }

    @Override
    public PageResult<ClientFeedbackSnapshot> searchClientFeedback(
            Long clientId, Long offererId, String keyword, Integer ratingMin, Integer ratingMax,
            int page, int size) {
        if (clientId == null && offererId == null) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        Page<ClientFeedback> result = offererId != null
                ? clientFeedbackPersistencePort.findByOffererIdAndFilters(
                        offererId, keyword, ratingMin, ratingMax, PageRequest.of(page, size))
                : clientFeedbackPersistencePort.findByClientIdAndFilters(
                        clientId, keyword, ratingMin, ratingMax, PageRequest.of(page, size));
        return toPageResult(result, this::toClientFeedbackSnapshot);
    }

    // ---------------------------------------------------------------------
    // FeedbackCommandApi
    // ---------------------------------------------------------------------

    @Override
    public boolean revertServiceFeedback(Long feedbackId) {
        return serviceFeedbackServicePort.revertFeedbackById(feedbackId);
    }

    @Override
    public boolean revertClientFeedback(Long feedbackId) {
        return clientFeedbackServicePort.revertFeedbackById(feedbackId);
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private <T, R> PageResult<R> toPageResult(Page<T> page, java.util.function.Function<T, R> mapper) {
        return new PageResult<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private ServiceFeedbackSnapshot toServiceFeedbackSnapshot(ServiceFeedback feedback) {
        return new ServiceFeedbackSnapshot(
                feedback.getId(),
                feedback.getRequestId(),
                feedback.getServiceId(),
                feedback.getClientId(),
                feedback.getRating(),
                feedback.getComment(),
                serviceFeedbackTagPersistencePort.findTagIdsByFeedbackId(feedback.getId()),
                feedback.getCreatedAt());
    }

    private ClientFeedbackSnapshot toClientFeedbackSnapshot(ClientFeedback feedback) {
        return new ClientFeedbackSnapshot(
                feedback.getId(),
                feedback.getRequestId(),
                feedback.getOffererId(),
                feedback.getClientId(),
                feedback.getRating(),
                feedback.getComment(),
                clientFeedbackTagPersistencePort.findTagIdsByFeedbackId(feedback.getId()),
                feedback.getCreatedAt());
    }
}
