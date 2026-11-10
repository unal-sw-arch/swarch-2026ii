package com.parosurvivors.serviya.profiles.infrastructure.repositories;

import com.parosurvivors.serviya.profiles.infrastructure.entities.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {
    Optional<UserProfileEntity> findByUserId(Long userId);
    List<UserProfileEntity> findByUserIdIn(Collection<Long> userIds);

    @Query("SELECT p.userId FROM UserProfileEntity p WHERE LOWER(p.fullName) LIKE :pattern")
    List<Long> findUserIdsByFullName(@Param("pattern") String pattern);
}
