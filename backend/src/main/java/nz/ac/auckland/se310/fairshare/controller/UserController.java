package nz.ac.auckland.se310.fairshare.controller;

import java.util.Map;
import nz.ac.auckland.se310.fairshare.UserService;
import nz.ac.auckland.se310.fairshare.dto.LoginRequest;
import nz.ac.auckland.se310.fairshare.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/register")
  public ResponseEntity<String> register(@RequestBody User user) {
        try {
            userService.register(user);
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already in use");
        }
    }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    try {
      User user = userService.login(request.email(), request.password());
      return ResponseEntity.ok(Map.of(
          "message", "Login successful",
          "user", serializeUser(user)
      ));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getUser(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(Map.of("user", serializeUser(userService.getUserById(id))));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
    try {
      User user = userService.updateUser(id, updatedUser);
      return ResponseEntity.ok(Map.of(
          "message", "Profile updated successfully",
          "user", serializeUser(user)
      ));
    } catch (IllegalArgumentException e) {
      if ("User not found".equals(e.getMessage())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
      }
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  private Map<String, Object> serializeUser(User user) {
    return Map.of(
        "id", user.getId(),
        "username", user.getUsername(),
        "email", user.getEmail(),
        "country", user.getCountry() == null ? null : user.getCountry().name(),
        "currency", user.getCurrency() == null ? null : user.getCurrency().name()
    );
  }

}
