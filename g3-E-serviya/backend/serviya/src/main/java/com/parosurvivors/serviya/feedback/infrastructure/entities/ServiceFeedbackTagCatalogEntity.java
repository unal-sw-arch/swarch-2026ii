package com.parosurvivors.serviya.feedback.infrastructure.entities;

import com.parosurvivors.serviya.feedback.domain.TagSentiment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "service_feedback_tags_catalog")
@Getter
@Setter
public class ServiceFeedbackTagCatalogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(name = "tag_name", nullable = false, unique = true, length = 150)
    private String tagName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagSentiment sentiment;
}
