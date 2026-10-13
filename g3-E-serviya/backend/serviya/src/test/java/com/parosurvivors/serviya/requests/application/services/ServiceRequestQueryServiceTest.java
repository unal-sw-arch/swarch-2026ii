package com.parosurvivors.serviya.requests.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.requests.application.dto.item.RequestHistoryItem;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ServiceRequestQueryServiceTest {

    @Mock ServiceRequestReadPort serviceRequestReadPort;
    @Mock ServicePersistencePort servicePersistencePort;
    @Mock CategoryPersistencePort categoryPersistencePort;
    @Mock UserProfilePersistencePort userProfilePersistencePort;
    @Mock AddressPersistencePort addressPersistencePort;

    @InjectMocks ServiceRequestQueryService service;

    private static final Long REQUEST_ID = 1L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long SERVICE_ID = 30L;
    private static final Long ADDRESS_ID = 40L;

    // =====================================================
    // getClientRequests / getOffererRequests
    // =====================================================

    @Test
    void getClientRequests_delegatesToReadPort() {
        SearchServiceRequestsQuery query = new SearchServiceRequestsQuery(
                CLIENT_ID, null, null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ServiceRequestSummaryItem> expectedPage = new PageImpl<>(Collections.emptyList());

        when(serviceRequestReadPort.searchByClient(query, pageable)).thenReturn(expectedPage);

        Page<ServiceRequestSummaryItem> result = service.getClientRequests(query, pageable);

        assertThat(result).isSameAs(expectedPage);
    }

    @Test
    void getOffererRequests_delegatesToReadPort() {
        SearchServiceRequestsQuery query = new SearchServiceRequestsQuery(
                OFFERER_ID, null, null, null, null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ServiceRequestSummaryItem> expectedPage = new PageImpl<>(Collections.emptyList());

        when(serviceRequestReadPort.searchByOfferer(query, pageable)).thenReturn(expectedPage);

        Page<ServiceRequestSummaryItem> result = service.getOffererRequests(query, pageable);

        assertThat(result).isSameAs(expectedPage);
    }

    // =====================================================
    // getRequestDetailForParty
    // =====================================================

    @Test
    void getRequestDetailForParty_returnsDetail_whenClientViewsOwnRequest() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(servicePersistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(domainService()));
        when(categoryPersistencePort.findById(1L)).thenReturn(Optional.of(category()));
        when(userProfilePersistencePort.findByUserId(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));
        when(addressPersistencePort.findById(ADDRESS_ID)).thenReturn(Optional.of(address()));

        ServiceRequestDetailResult result = service.getRequestDetailForParty(REQUEST_ID, CLIENT_ID);

        assertThat(result.id()).isEqualTo(REQUEST_ID);
        assertThat(result.counterpartyId()).isEqualTo(OFFERER_ID);
        assertThat(result.counterpartyName()).isEqualTo("Maria Garcia");
        assertThat(result.serviceTitle()).isEqualTo("Plomeria");
        assertThat(result.categoryName()).isEqualTo("Hogar");
        assertThat(result.addressLine()).isEqualTo("Calle 123");
    }

    @Test
    void getRequestDetailForParty_returnsCounterparty_whenOffererViews() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(servicePersistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(domainService()));
        when(userProfilePersistencePort.findByUserId(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));
        when(addressPersistencePort.findById(ADDRESS_ID)).thenReturn(Optional.of(address()));

        ServiceRequestDetailResult result = service.getRequestDetailForParty(REQUEST_ID, OFFERER_ID);

        assertThat(result.counterpartyId()).isEqualTo(CLIENT_ID);
        assertThat(result.counterpartyName()).isEqualTo("Juan Perez");
    }

    @Test
    void getRequestDetailForParty_throws_whenNotParticipant() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getRequestDetailForParty(REQUEST_ID, 999L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getRequestDetailForParty_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequestDetailForParty(REQUEST_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRequestDetailForParty_handlesMissingService() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(servicePersistencePort.findById(SERVICE_ID)).thenReturn(Optional.empty());

        ServiceRequestDetailResult result = service.getRequestDetailForParty(REQUEST_ID, CLIENT_ID);

        assertThat(result.serviceTitle()).isNull();
        assertThat(result.categoryName()).isNull();
    }

    // =====================================================
    // getRequestDetailForAdmin
    // =====================================================

    @Test
    void getRequestDetailForAdmin_returnsBothParties() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(servicePersistencePort.findById(SERVICE_ID)).thenReturn(Optional.of(domainService()));
        when(categoryPersistencePort.findById(1L)).thenReturn(Optional.of(category()));
        when(userProfilePersistencePort.findByUserId(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));
        when(addressPersistencePort.findById(ADDRESS_ID)).thenReturn(Optional.of(address()));

        AdminRequestDetailResult result = service.getRequestDetailForAdmin(REQUEST_ID);

        assertThat(result.clientId()).isEqualTo(CLIENT_ID);
        assertThat(result.clientName()).isEqualTo("Juan Perez");
        assertThat(result.offererId()).isEqualTo(OFFERER_ID);
        assertThat(result.offererName()).isEqualTo("Maria Garcia");
        assertThat(result.serviceTitle()).isEqualTo("Plomeria");
    }

    @Test
    void getRequestDetailForAdmin_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequestDetailForAdmin(REQUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // getRequestHistory
    // =====================================================

    @Test
    void getRequestHistory_returnsSingleRequest() {
        ServiceRequest request = pendingRequest();
        request.setId(REQUEST_ID);
        request.setPreviousRequestId(null);

        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(serviceRequestReadPort.findByPreviousRequestId(REQUEST_ID)).thenReturn(Optional.empty());

        List<RequestHistoryItem> history = service.getRequestHistory(REQUEST_ID, CLIENT_ID, false);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).id()).isEqualTo(REQUEST_ID);
        assertThat(history.get(0).status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void getRequestHistory_returnsChainInChronologicalOrder() {
        ServiceRequest root = pendingRequest();
        root.setId(1L);
        root.setPreviousRequestId(null);

        ServiceRequest middle = pendingRequest();
        middle.setId(2L);
        middle.setPreviousRequestId(1L);
        middle.setStatus(RequestStatus.RESCHEDULED);

        ServiceRequest latest = pendingRequest();
        latest.setId(3L);
        latest.setPreviousRequestId(2L);

        when(serviceRequestReadPort.findById(3L)).thenReturn(Optional.of(latest));
        when(serviceRequestReadPort.findById(2L)).thenReturn(Optional.of(middle));
        when(serviceRequestReadPort.findById(1L)).thenReturn(Optional.of(root));
        when(serviceRequestReadPort.findByPreviousRequestId(1L)).thenReturn(Optional.of(middle));
        when(serviceRequestReadPort.findByPreviousRequestId(2L)).thenReturn(Optional.of(latest));
        when(serviceRequestReadPort.findByPreviousRequestId(3L)).thenReturn(Optional.empty());

        List<RequestHistoryItem> history = service.getRequestHistory(3L, CLIENT_ID, false);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).id()).isEqualTo(1L);
        assertThat(history.get(1).id()).isEqualTo(2L);
        assertThat(history.get(2).id()).isEqualTo(3L);
    }

    @Test
    void getRequestHistory_throws_whenNotParticipantAndNotAdmin() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getRequestHistory(REQUEST_ID, 999L, false))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getRequestHistory_allowsAdminBypass() {
        ServiceRequest request = pendingRequest();
        request.setPreviousRequestId(null);
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(serviceRequestReadPort.findByPreviousRequestId(REQUEST_ID)).thenReturn(Optional.empty());

        List<RequestHistoryItem> history = service.getRequestHistory(REQUEST_ID, 999L, true);

        assertThat(history).hasSize(1);
    }

    @Test
    void getRequestHistory_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequestHistory(REQUEST_ID, CLIENT_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
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
                .addressId(ADDRESS_ID)
                .scheduledDate(LocalDateTime.of(2026, 8, 3, 14, 0))
                .status(RequestStatus.PENDING)
                .requestedPrice(new BigDecimal("25.00"))
                .createdAt(LocalDateTime.now())
                .updatedStatusAt(LocalDateTime.now())
                .build();
    }

    private Service domainService() {
        return Service.builder()
                .id(SERVICE_ID)
                .title("Plomeria")
                .categoryId(1L)
                .priceHourly(new BigDecimal("25.00"))
                .averageDurationMinutes(120)
                .build();
    }

    private Category category() {
        return Category.builder().id(1L).name("Hogar").build();
    }

    private UserProfile clientProfile() {
        return UserProfile.builder()
                .userId(CLIENT_ID)
                .fullName("Juan Perez")
                .profilePhotoUrl("photo_juan.jpg")
                .build();
    }

    private UserProfile offererProfile() {
        return UserProfile.builder()
                .userId(OFFERER_ID)
                .fullName("Maria Garcia")
                .profilePhotoUrl("photo_maria.jpg")
                .build();
    }

    private Address address() {
        return Address.builder()
                .id(ADDRESS_ID)
                .addressLine("Calle 123")
                .city("Medellin")
                .latitude(new BigDecimal("-75.5700"))
                .longitude(new BigDecimal("6.2442"))
                .build();
    }
}
