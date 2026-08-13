package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder encoder;

  public UserService(UserRepository userRepository, PasswordEncoder encoder) {
    this.userRepository = userRepository;
    this.encoder = encoder;
  }

  public synchronized void register(User user) {

    if (userRepository.findByEmail(user.getEmail()).isPresent()) {
      throw new IllegalArgumentException("Email already in use");
    }

    String hashedPassword = encoder.encode(user.getPassword());
    user.setPassword(hashedPassword);
    user.setEmail(user.getEmail().trim().toLowerCase());

    userRepository.save(user);
  }
}
