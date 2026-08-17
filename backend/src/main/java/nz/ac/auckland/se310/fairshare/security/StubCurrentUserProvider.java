package nz.ac.auckland.se310.fairshare.security;

import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.model.User;
import org.springframework.stereotype.Component;

@Component
public class StubCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    public StubCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // TODO: replaced by a SecurityContext-backed implementation in issue #NN
    @Override
    public Long currentUserId() {
        return userRepository.findAll().stream()
                .findFirst()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No users in the database. Register one before using the dev profile."));
    }
}