package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.domain.UserProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfilePersistencePort {
    UserProfile save(UserProfile profile);
    UserProfile update(UserProfile profile);
    Optional<UserProfile> findById(Long id);
    Optional<UserProfile> findByUserId(Long userId);
    List<UserProfile> findByUserIdIn(Collection<Long> userIds);
    List<Long> findUserIdsByFullName(String nameFragment);
}
