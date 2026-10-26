package com.parosurvivors.serviya.requests.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RescheduleProposalTest {

    private static final Long PROPOSAL_ID = 100L;
    private static final Long REQUEST_ID = 1L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;

    // =====================================================
    // STATE QUERIES
    // =====================================================

    @Test
    void isPending_true_whenStatusPending() {
        assertThat(pendingProposal().isPending()).isTrue();
    }

    @Test
    void isPending_false_whenAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();
        assertThat(proposal.isPending()).isFalse();
    }

    @Test
    void isResolved_false_whenPending() {
        assertThat(pendingProposal().isResolved()).isFalse();
    }

    @Test
    void isResolved_true_whenAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();
        assertThat(proposal.isResolved()).isTrue();
    }

    @Test
    void isResolved_true_whenRejected() {
        RescheduleProposal proposal = pendingProposal();
        proposal.reject();
        assertThat(proposal.isResolved()).isTrue();
    }

    @Test
    void isResolved_true_whenCancelled() {
        RescheduleProposal proposal = pendingProposal();
        proposal.cancel();
        assertThat(proposal.isResolved()).isTrue();
    }

    @Test
    void isResolved_true_whenSuperseded() {
        RescheduleProposal proposal = pendingProposal();
        proposal.markSuperseded();
        assertThat(proposal.isResolved()).isTrue();
    }

    // =====================================================
    // ACCEPT
    // =====================================================

    @Test
    void accept_transitionFromPendingToAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposal.getRespondedAt()).isNotNull();
    }

    @Test
    void accept_idempotent_whenAlreadyAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();
        LocalDateTime previousRespondedAt = proposal.getRespondedAt();

        proposal.accept();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposal.getRespondedAt()).isEqualTo(previousRespondedAt);
    }

    @Test
    void accept_throws_whenStatusRejected() {
        RescheduleProposal proposal = pendingProposal();
        proposal.reject();

        assertThatThrownBy(proposal::accept)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("aceptar");
    }

    @Test
    void accept_throws_whenStatusCancelled() {
        RescheduleProposal proposal = pendingProposal();
        proposal.cancel();

        assertThatThrownBy(proposal::accept)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("aceptar");
    }

    // =====================================================
    // REJECT
    // =====================================================

    @Test
    void reject_transitionFromPendingToRejected() {
        RescheduleProposal proposal = pendingProposal();
        proposal.reject();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRespondedAt()).isNotNull();
    }

    @Test
    void reject_idempotent_whenAlreadyRejected() {
        RescheduleProposal proposal = pendingProposal();
        proposal.reject();
        LocalDateTime previousRespondedAt = proposal.getRespondedAt();

        proposal.reject();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRespondedAt()).isEqualTo(previousRespondedAt);
    }

    @Test
    void reject_throws_whenStatusAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();

        assertThatThrownBy(proposal::reject)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("rechazar");
    }

    // =====================================================
    // CANCEL
    // =====================================================

    @Test
    void cancel_transitionFromPendingToCancelled() {
        RescheduleProposal proposal = pendingProposal();
        proposal.cancel();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposal.getRespondedAt()).isNotNull();
    }

    @Test
    void cancel_idempotent_whenAlreadyCancelled() {
        RescheduleProposal proposal = pendingProposal();
        proposal.cancel();
        LocalDateTime previousRespondedAt = proposal.getRespondedAt();

        proposal.cancel();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposal.getRespondedAt()).isEqualTo(previousRespondedAt);
    }

    @Test
    void cancel_throws_whenStatusAccepted() {
        RescheduleProposal proposal = pendingProposal();
        proposal.accept();

        assertThatThrownBy(proposal::cancel)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("cancelar");
    }

    // =====================================================
    // MARK SUPERSEDED
    // =====================================================

    @Test
    void markSuperseded_transitionFromPendingToSuperseded() {
        RescheduleProposal proposal = pendingProposal();
        proposal.markSuperseded();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SUPERSEDED);
        assertThat(proposal.getRespondedAt()).isNotNull();
    }

    @Test
    void markSuperseded_idempotent_whenAlreadySuperseded() {
        RescheduleProposal proposal = pendingProposal();
        proposal.markSuperseded();
        LocalDateTime previousRespondedAt = proposal.getRespondedAt();

        proposal.markSuperseded();

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.SUPERSEDED);
        assertThat(proposal.getRespondedAt()).isEqualTo(previousRespondedAt);
    }

    @Test
    void markSuperseded_throws_whenStatusRejected() {
        RescheduleProposal proposal = pendingProposal();
        proposal.reject();

        assertThatThrownBy(proposal::markSuperseded)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("superar");
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private RescheduleProposal pendingProposal() {
        return RescheduleProposal.builder()
                .id(PROPOSAL_ID)
                .requestId(REQUEST_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .reason("Conflicto de horario")
                .proposedDate(LocalDateTime.of(2026, 8, 15, 10, 0))
                .status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
