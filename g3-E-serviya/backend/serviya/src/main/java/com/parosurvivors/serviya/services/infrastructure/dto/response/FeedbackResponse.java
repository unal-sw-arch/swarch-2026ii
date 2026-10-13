package com.parosurvivors.serviya.services.infrastructure.dto.response;

import java.time.LocalDateTime;

public record FeedbackResponse (
    Long id,
    Long requestId,
    Long userId,
    String userName,
    String userPhotoUrl,
    String comment,
    Integer rating,
    LocalDateTime createdAt
){}
