package com.parosurvivors.serviya.services.domain;

import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;

/** Empareja una reseña (ServiceFeedback) con el perfil de su autor, para mostrarla en el detalle del servicio. */
public record FeedbackUser(
    ServiceFeedback feedback,
    UserProfile user
) {}
