package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.PasswordResetToken;
import fit.iuh.edu.fashion.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByUserAndUsedFalseAndExpiresAtAfter(User user, LocalDateTime now);
    void deleteByUser(User user);
    void deleteByExpiresAtBefore(LocalDateTime now);
}

