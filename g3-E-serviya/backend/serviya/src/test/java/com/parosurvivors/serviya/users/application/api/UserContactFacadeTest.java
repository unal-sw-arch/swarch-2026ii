package com.parosurvivors.serviya.users.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserContactFacadeTest {

    @Mock private UserQueryServicePort userQueryServicePort;

    @InjectMocks private UserContactFacade facade;

    @Test
    void emailOfReturnsEmail() {
        User user = User.builder().id(3L).email("mail@example.com").roles(List.of()).build();
        when(userQueryServicePort.getUserById(3L)).thenReturn(user);

        assertThat(facade.emailOf(3L)).contains("mail@example.com");
    }

    @Test
    void emailOfReturnsEmptyWhenUserDoesNotExist() {
        when(userQueryServicePort.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThat(facade.emailOf(99L)).isEmpty();
    }
}
