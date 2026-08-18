package nz.ac.auckland.se310.fairshare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import nz.ac.auckland.se310.fairshare.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginServiceTests {

  @Test
  void testLoginWithValidCredentials() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    User user = new User(
        "testuser",
        encoder.encode("password123"),
        "test@example.com",
        User.Country.NEW_ZEALAND,
        User.Currency.NZD);

    when(repository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

    UserService userService = new UserService(repository, encoder);
    User result = userService.login("Test@Example.com", "password123");

    assertEquals("testuser", result.getUsername());
    assertEquals("test@example.com", result.getEmail());
  }

  @Test
  void testLoginWithIncorrectPassword() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    User user = new User(
        "testuser",
        encoder.encode("password123"),
        "test@example.com",
        User.Country.NEW_ZEALAND,
        User.Currency.NZD);

    when(repository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

    UserService userService = new UserService(repository, encoder);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> userService.login("test@example.com", "wrongpass"));

    assertEquals("Invalid email or password", exception.getMessage());
  }
}
