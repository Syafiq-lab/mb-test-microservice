package mb.be.profile.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);
    boolean existsByEmail(String email);
}
