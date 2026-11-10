package com.parosurvivors.chat.directory.infrastructure.adapters.output;

import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.directory.infrastructure.entities.UserDirectoryEntity;
import com.parosurvivors.chat.directory.infrastructure.repositories.UserDirectoryJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserDirectoryPersistenceAdapter implements UserDirectory {

    private final UserDirectoryJpaRepository repository;

    UserDirectoryPersistenceAdapter(UserDirectoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DirectoryUser> find(long userId) {
        return repository.findById(userId).map(e -> new DirectoryUser(e.getUserId(), e.getDisplayName(),
                e.getAvatarUrl(), e.getStatus(), e.getUpdatedAt()));
    }

    @Override
    @Transactional
    public void save(DirectoryUser u) {
        repository.save(new UserDirectoryEntity(u.userId(), u.displayName(), u.avatarUrl(), u.status(), u.updatedAt()));
    }
}
