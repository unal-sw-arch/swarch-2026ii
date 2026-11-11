package com.parosurvivors.serviya.requests.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.requests.api.dto.RequestSearchCriteria;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.requests.api.dto.RequestSummary;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestQueryServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RequestApiFacadeTest {

    @Mock
    private ServiceRequestQueryServicePort serviceRequestQueryServicePort;
    @Mock
    private ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    @Mock
    private ServiceRequestReadPort serviceRequestReadPort;

    @InjectMocks
    private RequestApiFacade facade;

    @Test
    void findByIdMapsOwnRequestData() {
        when(serviceRequestReadPort.findById(1L)).thenReturn(Optional.of(request()));

        RequestSnapshot snapshot = facade.findById(1L).orElseThrow();

        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.clientId()).isEqualTo(10L);
        assertThat(snapshot.offererId()).isEqualTo(20L);
        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.isCompleted()).isTrue();
        assertThat(snapshot.isTerminal()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        when(serviceRequestReadPort.findById(99L)).thenReturn(Optional.empty());

        assertThat(facade.findById(99L)).isEmpty();
        assertThat(facade.findDetail(99L)).isEmpty();
    }

    @Test
    void findDetailIncludesAuditFields() {
        when(serviceRequestReadPort.findById(1L)).thenReturn(Optional.of(request()));

        var detail = facade.findDetail(1L).orElseThrow();

        assertThat(detail.updatedBy()).isEqualTo(10L);
        assertThat(detail.serviceId()).isEqualTo(30L);
        assertThat(detail.addressId()).isEqualTo(40L);
    }

    @Test
    void countActiveClientRequestsDelegates() {
        when(serviceRequestQueryServicePort.countActiveClientRequests(10L)).thenReturn(3L);

        assertThat(facade.countActiveClientRequests(10L)).isEqualTo(3L);
    }

    @Test
    void countByStatusConvertsContractString() {
        when(serviceRequestReadPort.countByStatus(RequestStatus.COMPLETED)).thenReturn(12L);

        assertThat(facade.countByStatus("COMPLETED")).isEqualTo(12L);
    }

    @Test
    void cancelCommandsMapDomainToSnapshots() {
        when(serviceRequestCommandServicePort.cancelActiveRequestsForUser(10L))
                .thenReturn(List.of(request()));
        when(serviceRequestCommandServicePort.cancelActiveRequestsForRole(10L, true))
                .thenReturn(List.of(request()));

        assertThat(facade.cancelActiveRequestsForUser(10L)).hasSize(1);
        assertThat(facade.cancelActiveRequestsForRole(10L, true)).hasSize(1);

        verify(serviceRequestCommandServicePort).cancelActiveRequestsForUser(10L);
        verify(serviceRequestCommandServicePort).cancelActiveRequestsForRole(10L, true);
    }

    @Test
    void markAsNotProvidedDelegates() {
        facade.markAsNotProvided(1L, 9L);

        verify(serviceRequestCommandServicePort).markAsNotProvided(1L, 9L);
    }

    @Test
    void searchForClientMapsPageAndCriteria() {
        ServiceRequestSummaryItem item = new ServiceRequestSummaryItem(
                1L, "ACCEPTED", LocalDateTime.of(2026, 8, 3, 14, 0),
                new BigDecimal("25.00"), null, LocalDateTime.of(2026, 8, 1, 10, 0),
                30L, "Plomeria", "Hogar", 20L, "Maria", "photo.jpg", "Medellin");
        when(serviceRequestQueryServicePort.getClientRequests(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        RequestSearchCriteria criteria = new RequestSearchCriteria(
                List.of("ACCEPTED"), 30L, null, null, "plom", null, null, null, null);
        PageResult<RequestSummary> result = facade.searchForClient(10L, criteria, 0, 10);

        ArgumentCaptor<SearchServiceRequestsQuery> captor =
                ArgumentCaptor.forClass(SearchServiceRequestsQuery.class);
        verify(serviceRequestQueryServicePort).getClientRequests(captor.capture(), eq(PageRequest.of(0, 10)));
        assertThat(captor.getValue().viewerId()).isEqualTo(10L);
        assertThat(captor.getValue().statuses()).containsExactly("ACCEPTED");
        assertThat(captor.getValue().serviceId()).isEqualTo(30L);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).counterpartyId()).isEqualTo(20L);
    }

    @Test
    void searchForOffererDelegates() {
        when(serviceRequestQueryServicePort.getOffererRequests(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResult<RequestSummary> result = facade.searchForOfferer(20L, null, 0, 5);

        assertThat(result.content()).isEmpty();
        verify(serviceRequestQueryServicePort).getOffererRequests(any(), eq(PageRequest.of(0, 5)));
    }

    private ServiceRequest request() {
        return ServiceRequest.builder()
                .id(1L)
                .serviceId(30L)
                .previousRequestId(null)
                .clientId(10L)
                .offererId(20L)
                .addressId(40L)
                .scheduledDate(LocalDateTime.of(2026, 8, 3, 14, 0))
                .status(RequestStatus.COMPLETED)
                .requestedPrice(new BigDecimal("25.00"))
                .updatedBy(10L)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .completedAt(LocalDateTime.of(2026, 8, 3, 15, 0))
                .updatedStatusAt(LocalDateTime.of(2026, 8, 3, 15, 0))
                .build();
    }
}
