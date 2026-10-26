package com.parosurvivors.serviya.profiles.infrastructure.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "offerer_profiles")
@Getter
@Setter
public class OffererProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "whatsapp_number", nullable = false, length = 30)
    private String whatsappNumber;

    @Column(name = "public_description", columnDefinition = "TEXT")
    private String publicDescription;

    @Column(nullable = false, length = 255)
    private String specialty;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
