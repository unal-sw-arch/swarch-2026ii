package com.parosurvivors.chat.directory.infrastructure.entities;

import com.parosurvivors.chat.directory.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_directory")
public class UserDirectoryEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDirectoryEntity() {
    }

    public UserDirectoryEntity(Long userId, String displayName, String avatarUrl, UserStatus status, Instant updatedAt) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
