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
          "user", Map.of(
              "id", user.getId(),
              "username", user.getUsername(),
              "email", user.getEmail()
          )
      ));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
  }

}
