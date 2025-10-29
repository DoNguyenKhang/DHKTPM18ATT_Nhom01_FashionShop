package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.ChangePasswordRequest;
import fit.iuh.edu.fashion.dto.request.ForgotPasswordRequest;
import fit.iuh.edu.fashion.dto.request.LoginRequest;
import fit.iuh.edu.fashion.dto.request.RegisterRequest;
import fit.iuh.edu.fashion.dto.request.ResetPasswordRequest;
import fit.iuh.edu.fashion.dto.response.AuthResponse;
import fit.iuh.edu.fashion.dto.response.UserResponse;
import fit.iuh.edu.fashion.exception.DuplicateResourceException;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import fit.iuh.edu.fashion.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final CartRepository cartRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;
    private final EmailService emailService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

            // Save refresh token
            saveRefreshToken(user, refreshToken);

            // Update last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Audit log
            auditService.logAction("LOGIN", "User", user.getId(), null,
                    "User logged in: " + user.getEmail());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getExpirationDateFromToken(accessToken).getTime())
                    .user(mapToUserResponse(user))
                    .build();
        } catch (Exception e) {
            // Audit failed login
            auditService.logFailedAction("LOGIN", "User", "Failed login attempt for: " + request.getEmail());
            throw e;
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            auditService.logFailedAction("REGISTER", "User", "Email already exists: " + request.getEmail());
            throw new DuplicateResourceException("Email already exists");
        }

        // Check if phone already exists
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            auditService.logFailedAction("REGISTER", "User", "Phone already exists: " + request.getPhone());
            throw new DuplicateResourceException("Phone number already exists");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .isActive(true)
                .build();

        // Assign CUSTOMER role by default
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Customer role not found"));
        Set<Role> roles = new HashSet<>();
        roles.add(customerRole);
        user.setRoles(roles);

        user = userRepository.save(user);

        // Create customer profile
        CustomerProfile customerProfile = CustomerProfile.builder()
                .user(user)
                .loyaltyPoint(0)
                .build();
        customerProfileRepository.save(customerProfile);

        // Create cart for customer
        Cart cart = Cart.builder()
                .customer(user)
                .build();
        cartRepository.save(cart);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        saveRefreshToken(user, refreshToken);

        // Audit log
        auditService.logAction("REGISTER", "User", user.getId(), null,
                "New user registered: " + user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationDateFromToken(accessToken).getTime())
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        String email = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify refresh token exists in database
        RefreshToken storedToken = refreshTokenRepository.findByTokenAndUser(refreshToken, user)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new RuntimeException("Refresh token expired");
        }

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // Delete old refresh token and save new one
        refreshTokenRepository.delete(storedToken);
        saveRefreshToken(user, newRefreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationDateFromToken(newAccessToken).getTime())
                .user(mapToUserResponse(user))
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            String email = jwtTokenProvider.extractUsername(refreshToken);
            userRepository.findByEmail(email).ifPresent(user ->
                refreshTokenRepository.findByTokenAndUser(refreshToken, user)
                        .ifPresent(refreshTokenRepository::delete)
            );
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email not found"));

        // Delete any existing unused tokens for this user
        passwordResetTokenRepository.deleteByUser(user);

        // Generate reset token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);
        // Send password reset email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            auditService.logAction("FORGOT_PASSWORD", "User", user.getId(), null,
                    "Password reset email sent to: " + user.getEmail());
        } catch (Exception e) {
            // Log error but still save token (user can try again)
            auditService.logFailedAction("FORGOT_PASSWORD", "User",
                    "Failed to send reset email to: " + user.getEmail());
            // For development, also print to console
            System.out.println("Failed to send email. Reset link: http://localhost:8080/reset-password?token=" + token);
            throw new RuntimeException("Failed to send email. Please contact administrator.");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (resetToken.getUsed()) {
            throw new RuntimeException("Reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new RuntimeException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        auditService.logAction("RESET_PASSWORD", "User", user.getId(), null,
                "Password reset completed for: " + user.getEmail());
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditService.logFailedAction("CHANGE_PASSWORD", "User",
                    "Failed password change attempt for: " + email);
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        auditService.logAction("CHANGE_PASSWORD", "User", user.getId(), null,
                "Password changed for: " + user.getEmail());
    }

    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .isActive(user.getIsActive())
                .roles(user.getRoles().stream()
                        .map(Role::getCode)
                        .collect(Collectors.toSet()))
                .build();
    }
}
