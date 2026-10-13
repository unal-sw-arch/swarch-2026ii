package com.parosurvivors.serviya.profiles.infrastructure.entities;

import com.parosurvivors.serviya.shared.security.PiiAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "addresses")
@Getter
@Setter
public class AddressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Cifrado AES-256-GCM. Pendiente el AttributeConverter de cifrado (ver NOTAS.txt). */
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "address_line", nullable = false, columnDefinition = "VARBINARY(1024)")
    private String addressLine;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
