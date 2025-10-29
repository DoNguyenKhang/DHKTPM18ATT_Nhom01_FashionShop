package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.ChangePasswordRequest;
import fit.iuh.edu.fashion.dto.request.UpdateProfileRequest;
import fit.iuh.edu.fashion.dto.response.ProfileResponse;
import fit.iuh.edu.fashion.models.CustomerProfile;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.UserRepository;
import fit.iuh.edu.fashion.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ProfileResponse response = mapToProfileResponse(user);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        try {
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Cập nhật thông tin cơ bản
            user.setFullName(request.getFullName());
            user.setPhone(request.getPhone());

            // Cập nhật hoặc tạo customer profile
            CustomerProfile profile = user.getCustomerProfile();
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setUser(user);
                profile.setUserId(user.getId());
                profile.setLoyaltyPoint(0);
            }

            if (request.getGender() != null && !request.getGender().isEmpty()) {
                profile.setGender(CustomerProfile.Gender.valueOf(request.getGender()));
            }
            profile.setBirthday(request.getBirthday());

            user.setCustomerProfile(profile);
            User savedUser = userRepository.save(user);

            return ResponseEntity.ok(mapToProfileResponse(savedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Không thể cập nhật thông tin: " + e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        try {
            String email = authentication.getName();
            authService.changePassword(email, request);
            return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private ProfileResponse mapToProfileResponse(User user) {
        ProfileResponse.ProfileResponseBuilder builder = ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .createdAt(user.getCreatedAt());

        if (user.getCustomerProfile() != null) {
            CustomerProfile profile = user.getCustomerProfile();
            builder.gender(profile.getGender() != null ? profile.getGender().name() : null)
                    .birthday(profile.getBirthday())
                    .loyaltyPoint(profile.getLoyaltyPoint());
        }

        return builder.build();
    }
}
