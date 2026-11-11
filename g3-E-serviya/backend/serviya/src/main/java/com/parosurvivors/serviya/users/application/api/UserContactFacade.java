package com.parosurvivors.serviya.users.application.api;

import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.api.UserContactApi;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion minima de {@link UserContactApi}. Solo depende de {@code UserQueryServicePort},
 * por lo que puede ser consumida por notifications sin crear el ciclo
 * notifications -> user contact -> user service -> notification service.
 */
@Component
@RequiredArgsConstructor
public class UserContactFacade implements UserContactApi {

    private final UserQueryServicePort userQueryServicePort;

    @Override
    public Optional<String> emailOf(Long userId) {
        try {
            return Optional.ofNullable(userQueryServicePort.getUserById(userId).getEmail());
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }
}
