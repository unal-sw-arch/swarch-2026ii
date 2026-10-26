package com.parosurvivors.serviya.requests.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ServiceRequestTest {

    private static final Long REQUEST_ID = 1L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long SERVICE_ID = 30L;

    // =====================================================
    // STATE QUERIES
    // =====================================================

    @Test
    void isPending_true_whenStatusPending() {
        assertThat(pendingRequest().isPending()).isTrue();
    }

    @Test
    void isPending_false_whenAccepted() {
        assertThat(acceptedRequest().isPending()).isFalse();
    }

    @Test
    void isAccepted_true_whenStatusAccepted() {
        assertThat(acceptedRequest().isAccepted()).isTrue();
    }

    @Test
    void isCompleted_true_whenStatusCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);
        assertThat(request.isCompleted()).isTrue();
    }

    @Test
    void isTerminal_true_forRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_true_forCancelled() {
        ServiceRequest request = pendingRequest();
        request.cancel(CLIENT_ID);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_true_forCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_true_forRescheduled() {
        ServiceRequest request = pendingRequest();
        request.markRescheduled(CLIENT_ID);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_true_forNotProvided() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsNotProvided(OFFERER_ID);
        assertThat(request.isTerminal()).isTrue();
    }

    @Test
    void isTerminal_false_forPending() {
        assertThat(pendingRequest().isTerminal()).isFalse();
    }

    @Test
    void isTerminal_false_forAccepted() {
        assertThat(acceptedRequest().isTerminal()).isFalse();
    }

    @Test
    void isTerminal_false_forPresumablyCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        assertThat(request.isTerminal()).isFalse();
    }

    // =====================================================
    // ACCEPT
    // =====================================================

    @Test
    void accept_transitionFromPendingToAccepted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(request.getUpdatedBy()).isEqualTo(OFFERER_ID);
        assertThat(request.getUpdatedStatusAt()).isNotNull();
    }

    @Test
    void accept_idempotent_whenAlreadyAccepted() {
        ServiceRequest request = acceptedRequest();
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.accept(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void accept_throws_whenStatusRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);

        assertThatThrownBy(() -> request.accept(OFFERER_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void accept_throws_whenStatusCancelled() {
        ServiceRequest request = pendingRequest();
        request.cancel(CLIENT_ID);

        assertThatThrownBy(() -> request.accept(OFFERER_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    // =====================================================
    // REJECT
    // =====================================================

    @Test
    void reject_transitionFromPendingToRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request.getUpdatedBy()).isEqualTo(OFFERER_ID);
    }

    @Test
    void reject_idempotent_whenAlreadyRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.reject(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void reject_throws_whenStatusAccepted() {
        assertThatThrownBy(() -> acceptedRequest().reject(OFFERER_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    // =====================================================
    // CANCEL
    // =====================================================

    @Test
    void cancel_fromPending_setsCancelled() {
        ServiceRequest request = pendingRequest();
        request.cancel(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getUpdatedBy()).isEqualTo(CLIENT_ID);
    }

    @Test
    void cancel_fromAccepted_setsCancelled() {
        ServiceRequest request = acceptedRequest();
        request.cancel(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void cancel_idempotent_whenAlreadyCancelled() {
        ServiceRequest request = pendingRequest();
        request.cancel(CLIENT_ID);
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.cancel(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void cancel_throws_whenStatusRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);

        assertThatThrownBy(() -> request.cancel(CLIENT_ID))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("cancelar");
    }

    @Test
    void cancel_throws_whenStatusCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);

        assertThatThrownBy(() -> request.cancel(CLIENT_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void cancel_throws_whenStatusPresumablyCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);

        assertThatThrownBy(() -> request.cancel(CLIENT_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    // =====================================================
    // MARK AS PRESUMABLY COMPLETED
    // =====================================================

    @Test
    void markAsPresumablyCompleted_fromAccepted() {
        ServiceRequest request = acceptedRequest();
        request.markAsPresumablyCompleted(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PRESUMABLY_COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
        assertThat(request.getUpdatedBy()).isEqualTo(OFFERER_ID);
    }

    @Test
    void markAsPresumablyCompleted_idempotent_whenAlreadyPresumablyCompleted() {
        ServiceRequest request = acceptedRequest();
        request.markAsPresumablyCompleted(OFFERER_ID);
        LocalDateTime previousCompletedAt = request.getCompletedAt();

        request.markAsPresumablyCompleted(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PRESUMABLY_COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(previousCompletedAt);
    }

    @Test
    void markAsPresumablyCompleted_idempotent_whenAlreadyCompleted() {
        ServiceRequest request = acceptedRequest();
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);

        request.markAsPresumablyCompleted(OFFERER_ID);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void markAsPresumablyCompleted_throws_whenStatusPending() {
        assertThatThrownBy(() -> pendingRequest().markAsPresumablyCompleted(OFFERER_ID))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("marcar como prestada");
    }

    // =====================================================
    // CONFIRM COMPLETION
    // =====================================================

    @Test
    void confirmCompletion_fromPresumablyCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getUpdatedBy()).isEqualTo(CLIENT_ID);
    }

    @Test
    void confirmCompletion_idempotent_whenAlreadyCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.confirmCompletion(CLIENT_ID);
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.confirmCompletion(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void confirmCompletion_throws_whenStatusAccepted() {
        assertThatThrownBy(() -> acceptedRequest().confirmCompletion(CLIENT_ID))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("confirmar");
    }

    // =====================================================
    // MARK AS NOT PROVIDED
    // =====================================================

    @Test
    void markAsNotProvided_fromAccepted() {
        ServiceRequest request = acceptedRequest();
        request.markAsNotProvided(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.NOT_PROVIDED);
        assertThat(request.getUpdatedBy()).isEqualTo(OFFERER_ID);
    }

    @Test
    void markAsNotProvided_fromPresumablyCompleted() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        request.markAsPresumablyCompleted(OFFERER_ID);
        request.markAsNotProvided(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.NOT_PROVIDED);
    }

    @Test
    void markAsNotProvided_idempotent_whenAlreadyNotProvided() {
        ServiceRequest request = acceptedRequest();
        request.markAsNotProvided(OFFERER_ID);
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.markAsNotProvided(OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.NOT_PROVIDED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void markAsNotProvided_throws_whenStatusPending() {
        assertThatThrownBy(() -> pendingRequest().markAsNotProvided(OFFERER_ID))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("aceptada");
    }

    // =====================================================
    // MARK RESCHEDULED
    // =====================================================

    @Test
    void markRescheduled_fromPending() {
        ServiceRequest request = pendingRequest();
        request.markRescheduled(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESCHEDULED);
        assertThat(request.getUpdatedBy()).isEqualTo(CLIENT_ID);
    }

    @Test
    void markRescheduled_fromAccepted() {
        ServiceRequest request = acceptedRequest();
        request.markRescheduled(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESCHEDULED);
    }

    @Test
    void markRescheduled_idempotent_whenAlreadyRescheduled() {
        ServiceRequest request = pendingRequest();
        request.markRescheduled(CLIENT_ID);
        LocalDateTime previousUpdatedStatusAt = request.getUpdatedStatusAt();

        request.markRescheduled(CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESCHEDULED);
        assertThat(request.getUpdatedStatusAt()).isEqualTo(previousUpdatedStatusAt);
    }

    @Test
    void markRescheduled_throws_whenStatusRejected() {
        ServiceRequest request = pendingRequest();
        request.reject(OFFERER_ID);

        assertThatThrownBy(() -> request.markRescheduled(CLIENT_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    // =====================================================
    // RESCHEDULE TO (Prototype)
    // =====================================================

    @Test
    void rescheduleTo_createsReplacementLinkedByPreviousRequestId() {
        ServiceRequest original = pendingRequest();
        original.setId(REQUEST_ID);
        LocalDateTime newDate = LocalDateTime.of(2026, 8, 15, 10, 0);

        ServiceRequest replacement = original.rescheduleTo(newDate, RequestStatus.PENDING, CLIENT_ID);

        assertThat(replacement.getId()).isNull();
        assertThat(replacement.getPreviousRequestId()).isEqualTo(REQUEST_ID);
        assertThat(replacement.getScheduledDate()).isEqualTo(newDate);
        assertThat(replacement.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(replacement.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(replacement.getOffererId()).isEqualTo(OFFERER_ID);
        assertThat(replacement.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(replacement.getCompletedAt()).isNull();
        assertThat(replacement.getCreatedAt()).isNotNull();
        assertThat(replacement.getUpdatedStatusAt()).isNotNull();
        assertThat(replacement).isNotSameAs(original);
    }

    @Test
    void rescheduleTo_copiesPriceFromOriginal() {
        ServiceRequest original = pendingRequest();
        original.setRequestedPrice(new BigDecimal("50.00"));

        ServiceRequest replacement = original.rescheduleTo(
                LocalDateTime.of(2026, 8, 15, 10, 0), RequestStatus.PENDING, CLIENT_ID);

        assertThat(replacement.getRequestedPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // =====================================================
    // FULL FLOW
    // =====================================================

    @Test
    void fullFlow_pending_to_completed() {
        ServiceRequest request = pendingRequest();

        request.accept(OFFERER_ID);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.ACCEPTED);

        request.markAsPresumablyCompleted(OFFERER_ID);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PRESUMABLY_COMPLETED);

        request.confirmCompletion(CLIENT_ID);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.isTerminal()).isTrue();
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private ServiceRequest pendingRequest() {
        return ServiceRequest.builder()
                .id(REQUEST_ID)
                .serviceId(SERVICE_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .addressId(40L)
                .scheduledDate(LocalDateTime.of(2026, 8, 1, 14, 0))
                .status(RequestStatus.PENDING)
                .requestedPrice(new BigDecimal("25.00"))
                .createdAt(LocalDateTime.now())
                .updatedStatusAt(LocalDateTime.now())
                .build();
    }

    private ServiceRequest acceptedRequest() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        return request;
    }
}
