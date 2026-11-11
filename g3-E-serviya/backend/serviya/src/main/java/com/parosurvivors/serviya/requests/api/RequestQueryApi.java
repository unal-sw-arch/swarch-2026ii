package com.parosurvivors.serviya.requests.api;

import com.parosurvivors.serviya.requests.api.dto.RequestDetailSnapshot;
import com.parosurvivors.serviya.requests.api.dto.RequestSearchCriteria;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.requests.api.dto.RequestSummary;
import com.parosurvivors.serviya.shared.contract.PageResult;
import java.util.Optional;

/**
 * Consultas publicas del modulo requests.
 */
public interface RequestQueryApi {

    Optional<RequestSnapshot> findById(Long requestId);

    Optional<RequestDetailSnapshot> findDetail(Long requestId);

    long countActiveClientRequests(Long clientId);

    long countByStatus(String status);

    PageResult<RequestSummary> searchForClient(
            Long clientId, RequestSearchCriteria criteria, int page, int size);

    PageResult<RequestSummary> searchForOfferer(
            Long offererId, RequestSearchCriteria criteria, int page, int size);
}
