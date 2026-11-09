package com.kanasta.accounts;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// REST API of the Accounts Service. The API Gateway adds the X-User-Id header
// after checking the session token.
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    record RegisterRequest(String name, String email, String password, String phone, String address) {}
    record LoginRequest(String email, String password) {}
    record ProfileRequest(String name, String phone, String address) {}
    record PasswordRequest(String currentPassword, String newPassword) {}
    record ActiveRequest(boolean active) {}
    record SessionResponse(String token, User user) {}

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final NotificationPublisher notifications;

    public AccountController(UserRepository users, BCryptPasswordEncoder encoder, NotificationPublisher notifications) {
        this.users = users;
        this.encoder = encoder;
        this.notifications = notifications;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@RequestBody RegisterRequest body) {
        String email = clean(body.email()).toLowerCase();
        if (clean(body.name()).isEmpty() || !email.contains("@")) {
            throw error(HttpStatus.BAD_REQUEST, "Escribe tu nombre y un correo válido");
        }
        checkPassword(body.password());
        if (users.findByEmail(email) != null) {
            throw error(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        User user = users.create(clean(body.name()), email, encoder.encode(body.password()),
                clean(body.phone()), clean(body.address()), "CUSTOMER");
        notifications.publish("ACCOUNT_CREATED", user);
        return new SessionResponse(users.createSession(user.id()), user);
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest body) {
        User user = users.findByEmail(clean(body.email()).toLowerCase());
        if (user == null || body.password() == null || !encoder.matches(body.password(), user.passwordHash())) {
            throw error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        if (!user.active()) {
            throw error(HttpStatus.FORBIDDEN, "Esta cuenta está desactivada. Comunícate con la tienda");
        }
        return new SessionResponse(users.createSession(user.id()), user);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null) {
            users.deleteSession(auth.replace("Bearer ", ""));
        }
        return Map.of("ok", true);
    }

    // Used by the API Gateway on every request that carries a token
    @GetMapping("/session")
    public User session(@RequestHeader(value = "Authorization", required = false) String auth) {
        User user = auth == null ? null : users.findByToken(auth.replace("Bearer ", ""));
        if (user == null || !user.active()) {
            throw error(HttpStatus.UNAUTHORIZED, "La sesión no es válida");
        }
        return user;
    }

    @GetMapping("/me")
    public User me(@RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        return currentUser(userId);
    }

    @PutMapping("/me")
    public User updateProfile(@RequestHeader(value = "X-User-Id", required = false) Integer userId,
                              @RequestBody ProfileRequest body) {
        User user = currentUser(userId);
        if (clean(body.name()).isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "El nombre no puede quedar vacío");
        }
        users.updateProfile(user.id(), clean(body.name()), clean(body.phone()), clean(body.address()));
        return users.findById(user.id());
    }

    @PutMapping("/me/password")
    public Map<String, Boolean> changePassword(@RequestHeader(value = "X-User-Id", required = false) Integer userId,
                                               @RequestBody PasswordRequest body) {
        User user = currentUser(userId);
        if (body.currentPassword() == null || !encoder.matches(body.currentPassword(), user.passwordHash())) {
            throw error(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }
        checkPassword(body.newPassword());
        users.updatePassword(user.id(), encoder.encode(body.newPassword()));
        notifications.publish("PASSWORD_CHANGED", user);
        return Map.of("ok", true);
    }

    // ---------- admin only (the API Gateway checks the role) ----------

    @GetMapping("/users")
    public List<User> listUsers() {
        return users.findAll();
    }

    @PutMapping("/users/{id}/active")
    public User setActive(@PathVariable int id, @RequestBody ActiveRequest body) {
        User user = users.findById(id);
        if (user == null) {
            throw error(HttpStatus.NOT_FOUND, "La cuenta no existe");
        }
        if ("ADMIN".equals(user.role())) {
            throw error(HttpStatus.BAD_REQUEST, "La cuenta del administrador no se puede desactivar");
        }
        users.updateActive(id, body.active());
        if (!body.active()) {
            users.deleteSessionsOf(id);
        }
        return users.findById(id);
    }

    // ---------- helpers ----------

    private User currentUser(Integer userId) {
        User user = userId == null ? null : users.findById(userId);
        if (user == null) {
            throw error(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión");
        }
        return user;
    }

    private void checkPassword(String password) {
        if (password == null || password.length() < 6) {
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
