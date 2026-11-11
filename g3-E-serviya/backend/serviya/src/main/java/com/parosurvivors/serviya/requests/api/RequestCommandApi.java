package com.parosurvivors.serviya.requests.api;

import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import java.util.List;

/**
 * Comandos publicos del modulo requests.
 */
public interface RequestCommandApi {

    List<RequestSnapshot> cancelActiveRequestsForUser(Long userId);

    List<RequestSnapshot> cancelActiveRequestsForRole(Long userId, boolean asOfferer);

    void markAsNotProvided(Long requestId, Long actorId);
}
