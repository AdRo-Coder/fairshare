package nz.ac.auckland.se310.fairshare;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.auckland.se310.fairshare.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}