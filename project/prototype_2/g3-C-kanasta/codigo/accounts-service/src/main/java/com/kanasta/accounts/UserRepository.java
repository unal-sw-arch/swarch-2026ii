package com.kanasta.accounts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// The only class that talks to the Accounts DB
@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getString("role"),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    public User findById(int id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", MAPPER, id).stream().findFirst().orElse(null);
    }

    public User findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", MAPPER, email).stream().findFirst().orElse(null);
    }

    public User findByToken(String token) {
        return jdbc.query("SELECT u.* FROM users u JOIN sessions s ON s.user_id = u.id WHERE s.token = ?", MAPPER, token)
                .stream().findFirst().orElse(null);
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY role, name", MAPPER);
    }

    public User create(String name, String email, String passwordHash, String phone, String address, String role) {
        Integer id = jdbc.queryForObject(
                "INSERT INTO users (name, email, password_hash, phone, address, role) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Integer.class, name, email, passwordHash, phone, address, role);
        return findById(id);
    }

    public void updateProfile(int id, String name, String phone, String address) {
        jdbc.update("UPDATE users SET name = ?, phone = ?, address = ? WHERE id = ?", name, phone, address, id);
    }

    public void updatePassword(int id, String passwordHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void updateActive(int id, boolean active) {
        jdbc.update("UPDATE users SET active = ? WHERE id = ?", active, id);
    }

    public String createSession(int userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sessions (token, user_id) VALUES (?, ?)", token, userId);
        return token;
    }

    public void deleteSession(String token) {
        jdbc.update("DELETE FROM sessions WHERE token = ?", token);
    }

    public void deleteSessionsOf(int userId) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", userId);
    }
}
