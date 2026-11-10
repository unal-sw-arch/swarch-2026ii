package com.parosurvivors.chat.directory.application.ports.output;

import com.parosurvivors.chat.directory.domain.DirectoryUser;
import java.util.Optional;

public interface UserDirectory {

    Optional<DirectoryUser> find(long userId);

    void save(DirectoryUser user);
}
