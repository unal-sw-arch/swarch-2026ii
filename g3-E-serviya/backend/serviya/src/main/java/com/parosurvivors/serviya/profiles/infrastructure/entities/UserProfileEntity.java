package com.parosurvivors.serviya.profiles.infrastructure.entities;

import com.parosurvivors.serviya.profiles.domain.ProfileType;
import com.parosurvivors.serviya.shared.security.PiiAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    /** Cifrado AES-256-GCM en BD (VARBINARY); en claro como String via {@link PiiAttributeConverter}. */
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "document_number", nullable = false, columnDefinition = "VARBINARY(512)")
    private String documentNumber;

    /** Cifrado AES-256-GCM en BD (VARBINARY); en claro como String via {@link PiiAttributeConverter}. */
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "phone_number", nullable = false, columnDefinition = "VARBINARY(512)")
    private String phoneNumber;

    @Column(name = "primary_address_id")
    private Long primaryAddressId;

    @Column(name = "profile_photo_url", length = 1000)
    private String profilePhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", nullable = false)
    private ProfileType profileType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
