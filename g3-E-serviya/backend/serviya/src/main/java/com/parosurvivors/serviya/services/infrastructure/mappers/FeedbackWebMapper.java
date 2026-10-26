package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.services.infrastructure.dto.response.FeedbackResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface FeedbackWebMapper {

    @Mapping(target = "id", source = "feedback.id")
    @Mapping(target = "requestId", source = "feedback.requestId")
    @Mapping(target = "comment", source = "feedback.comment")
    @Mapping(target = "createdAt", source = "feedback.createdAt")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "userPhotoUrl", source = "user.profilePhotoUrl")
    FeedbackResponse toResponse(ServiceFeedback feedback, UserProfile user);

}
