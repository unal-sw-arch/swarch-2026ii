package com.parosurvivors.serviya.users.infrastructure.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserReadPort userReadPort;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter filter;

    @Test
    void rejectsBannedUsersBeforeContinuingTheChain() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token-123");
        Authentication auth = new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of());
        when(jwtService.resolve("token-123")).thenReturn(Optional.of(auth));
        when(userReadPort.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).banned(true).build()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void revalidatesRolesFromDatabaseOnEachRequest() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token-123");

        Authentication jwtAuth = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtService.resolve("token-123")).thenReturn(Optional.of(jwtAuth));

        User dbUser = User.builder()
                .id(7L)
                .banned(false)
                .deletedAt(null)
                .roles(List.of(RoleName.CLIENT))
                .build();
        when(userReadPort.findById(7L)).thenReturn(Optional.of(dbUser));

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsDeletedUsers() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token-123");
        Authentication auth = new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of());
        when(jwtService.resolve("token-123")).thenReturn(Optional.of(auth));
        when(userReadPort.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).deletedAt(java.time.LocalDateTime.now()).build()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsNonExistentUsers() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token-123");
        Authentication auth = new UsernamePasswordAuthenticationToken(7L, null, java.util.List.of());
        when(jwtService.resolve("token-123")).thenReturn(Optional.of(auth));
        when(userReadPort.findById(7L)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }
}
