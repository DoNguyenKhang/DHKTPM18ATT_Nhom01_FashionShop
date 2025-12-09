package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.CreateUserRequest;
import fit.iuh.edu.fashion.dto.request.UpdateUserRequest;
import fit.iuh.edu.fashion.dto.request.UpdateUserStatusRequest;
import fit.iuh.edu.fashion.dto.response.UserResponse;
import fit.iuh.edu.fashion.models.Role;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.CustomerProfileRepository;
import fit.iuh.edu.fashion.repositories.EmployeeProfileRepository;
import fit.iuh.edu.fashion.repositories.RoleRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(mapToUserResponse(user));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsActive(request.getActive());
        User updatedUser = userRepository.save(user);

        return ResponseEntity.ok(mapToUserResponse(updatedUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Tạo mật khẩu mới ngẫu nhiên (8 ký tự)
        String newPassword = generateRandomPassword(8);

        // Mã hóa và lưu mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // TODO: Gửi email cho người dùng với mật khẩu mới
        // Tạm thời trả về mật khẩu trong response (chỉ dùng cho development)

        return ResponseEntity.ok(Map.of(
                "message", "Cấp lại mật khẩu thành công",
                "newPassword", newPassword,
                "email", user.getEmail()
        ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            // Kiểm tra email đã tồn tại
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email đã tồn tại"));
            }

            // Tạo user mới
            User user = User.builder()
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .isActive(request.getIsActive())
                    .build();

            // Gán roles
            Set<Role> roles = new HashSet<>();
            boolean isStaff = false;
            boolean isCustomer = false;

            for (String roleName : request.getRoles()) {
                Role role = roleRepository.findByCode(roleName)
                        .orElseThrow(() -> new RuntimeException("Role không tồn tại: " + roleName));
                roles.add(role);

                if (roleName.equals("ADMIN") || roleName.startsWith("STAFF_")) {
                    isStaff = true;
                }
                if (roleName.equals("USER")) {
                    isCustomer = true;
                }
            }
            user.setRoles(roles);

            // Tạo profile tương ứng
            if (isStaff) {
                // Tạo EmployeeProfile cho nhân viên/admin
                fit.iuh.edu.fashion.models.EmployeeProfile employeeProfile =
                    fit.iuh.edu.fashion.models.EmployeeProfile.builder()
                        .user(user)
                        .userId(null) // Will be set by @MapsId
                        .employeeCode(generateEmployeeCode())
                        .position(getRolePosition(request.getRoles()))
                        .hireDate(java.time.LocalDate.now())
                        .build();
                user.setEmployeeProfile(employeeProfile);
            } else if (isCustomer) {
                // Tạo CustomerProfile cho khách hàng
                fit.iuh.edu.fashion.models.CustomerProfile customerProfile =
                    fit.iuh.edu.fashion.models.CustomerProfile.builder()
                        .user(user)
                        .userId(null) // Will be set by @MapsId
                        .loyaltyPoint(0)
                        .build();
                user.setCustomerProfile(customerProfile);
            }

            User savedUser = userRepository.save(user);
            return ResponseEntity.ok(mapToUserResponse(savedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể tạo người dùng: " + e.getMessage()));
        }
    }

    private String generateEmployeeCode() {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "EMP-" + timestamp;
    }

    private String getRolePosition(Set<String> roles) {
        if (roles.contains("ADMIN")) {
            return "Administrator";
        } else if (roles.contains("STAFF_PRODUCT")) {
            return "Product Manager";
        } else if (roles.contains("STAFF_SALES")) {
            return "Sales Manager";
        }
        return "Staff";
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Kiểm tra email đã tồn tại (nếu thay đổi email)
            if (!user.getEmail().equals(request.getEmail())) {
                if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Email đã tồn tại"));
                }
                user.setEmail(request.getEmail());
            }

            // Cập nhật thông tin
            user.setFullName(request.getFullName());
            user.setPhone(request.getPhone());
            user.setIsActive(request.getIsActive());

            // Cập nhật roles
            Set<Role> roles = new HashSet<>();
            boolean isStaff = false;
            boolean isCustomer = false;

            for (String roleName : request.getRoles()) {
                Role role = roleRepository.findByCode(roleName)
                        .orElseThrow(() -> new RuntimeException("Role không tồn tại: " + roleName));
                roles.add(role);

                if (roleName.equals("ADMIN") || roleName.startsWith("STAFF_")) {
                    isStaff = true;
                }
                if (roleName.equals("USER")) {
                    isCustomer = true;
                }
            }
            user.setRoles(roles);

            // Quản lý profile dựa trên role mới
            if (isStaff) {
                // Cần EmployeeProfile
                if (user.getEmployeeProfile() == null) {
                    // Tạo mới EmployeeProfile
                    fit.iuh.edu.fashion.models.EmployeeProfile employeeProfile =
                        fit.iuh.edu.fashion.models.EmployeeProfile.builder()
                            .user(user)
                            .userId(user.getId())
                            .employeeCode(generateEmployeeCode())
                            .position(getRolePosition(request.getRoles()))
                            .hireDate(java.time.LocalDate.now())
                            .build();
                    user.setEmployeeProfile(employeeProfile);
                } else {
                    // Cập nhật position nếu đã có
                    user.getEmployeeProfile().setPosition(getRolePosition(request.getRoles()));
                }

                // Xóa CustomerProfile nếu có (chuyển từ USER sang STAFF)
                if (user.getCustomerProfile() != null) {
                    customerProfileRepository.delete(user.getCustomerProfile());
                    user.setCustomerProfile(null);
                }
            } else if (isCustomer) {
                // Cần CustomerProfile
                if (user.getCustomerProfile() == null) {
                    // Tạo mới CustomerProfile
                    fit.iuh.edu.fashion.models.CustomerProfile customerProfile =
                        fit.iuh.edu.fashion.models.CustomerProfile.builder()
                            .user(user)
                            .userId(user.getId())
                            .loyaltyPoint(0)
                            .build();
                    user.setCustomerProfile(customerProfile);
                }

                // Xóa EmployeeProfile nếu có (chuyển từ STAFF sang USER)
                if (user.getEmployeeProfile() != null) {
                    employeeProfileRepository.delete(user.getEmployeeProfile());
                    user.setEmployeeProfile(null);
                }
            }

            User updatedUser = userRepository.save(user);
            return ResponseEntity.ok(mapToUserResponse(updatedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể cập nhật người dùng: " + e.getMessage()));
        }
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .isActive(user.getIsActive())
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream()
                        .map(Role::getCode)
                        .collect(Collectors.toSet()))
                .build();
    }
}
