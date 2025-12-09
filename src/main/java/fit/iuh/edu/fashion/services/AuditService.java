package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.models.AuditLog;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.AuditLogRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Async
    public void logAction(String action, String entityType, Long entityId, String oldValue, String newValue) {
        HttpServletRequest request = getCurrentRequest();
        logAction(action, entityType, entityId, oldValue, newValue, request);
    }

    /**
     * Enhanced logAction that accepts HttpServletRequest to avoid losing context in @Async
     */
    @Async
    public void logAction(String action, String entityType, Long entityId, String oldValue, String newValue, HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .status("SUCCESS")
                    .build();

            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                auditLog.setUsername(auth.getName());
                // Set userId if available
                userRepository.findByEmail(auth.getName()).ifPresent(user ->
                    auditLog.setUserId(user.getId())
                );
            }

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestMethod(request.getMethod());
                auditLog.setRequestUrl(request.getRequestURI());
            }

            auditLogRepository.save(auditLog);
            log.info("Audit log created: {} on {} {}", action, entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }

    @Async
    public void logFailedAction(String action, String entityType, String errorMessage) {
        try {
            HttpServletRequest request = getCurrentRequest();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .status("FAILED")
                    .errorMessage(errorMessage)
                    .build();

            if (auth != null && auth.isAuthenticated()) {
                auditLog.setUsername(auth.getName());
                userRepository.findByEmail(auth.getName()).ifPresent(user ->
                    auditLog.setUserId(user.getId())
                );
            }

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestMethod(request.getMethod());
                auditLog.setRequestUrl(request.getRequestURI());
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }

    // NEW: Log login attempts
    @Async
    public void logLoginAttempt(String username, boolean success, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .username(username)
                    .action("LOGIN")
                    .entityType("User")
                    .status(success ? "SUCCESS" : "FAILED")
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .requestMethod("POST")
                    .requestUrl("/api/auth/login")
                    .build();

            if (success) {
                userRepository.findByEmail(username).ifPresent(user -> {
                    auditLog.setUserId(user.getId());
                    auditLog.setEntityId(user.getId());
                });
            } else {
                auditLog.setErrorMessage("Invalid credentials");
            }

            auditLogRepository.save(auditLog);
            log.info("Login attempt logged: {} - {}", username, success ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            log.error("Failed to log login attempt", e);
        }
    }

    // NEW: Log logout
    @Async
    public void logLogout(String username) {
        try {
            HttpServletRequest request = getCurrentRequest();

            AuditLog auditLog = AuditLog.builder()
                    .username(username)
                    .action("LOGOUT")
                    .entityType("User")
                    .status("SUCCESS")
                    .build();

            userRepository.findByEmail(username).ifPresent(user -> {
                auditLog.setUserId(user.getId());
                auditLog.setEntityId(user.getId());
            });

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestMethod(request.getMethod());
                auditLog.setRequestUrl(request.getRequestURI());
            }

            auditLogRepository.save(auditLog);
            log.info("Logout logged: {}", username);
        } catch (Exception e) {
            log.error("Failed to log logout", e);
        }
    }

    // NEW: Log user registration
    @Async
    public void logUserRegistration(Long userId, String email) {
        try {
            HttpServletRequest request = getCurrentRequest();

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(email)
                    .action("REGISTER")
                    .entityType("User")
                    .entityId(userId)
                    .status("SUCCESS")
                    .newValue("New user registered")
                    .build();

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestMethod("POST");
                auditLog.setRequestUrl("/api/auth/register");
            }

            auditLogRepository.save(auditLog);
            log.info("User registration logged: {}", email);
        } catch (Exception e) {
            log.error("Failed to log user registration", e);
        }
    }

    // NEW: Log password change
    @Async
    public void logPasswordChange(String username, boolean success) {
        try {
            HttpServletRequest request = getCurrentRequest();

            AuditLog auditLog = AuditLog.builder()
                    .username(username)
                    .action("CHANGE_PASSWORD")
                    .entityType("User")
                    .status(success ? "SUCCESS" : "FAILED")
                    .build();

            userRepository.findByEmail(username).ifPresent(user -> {
                auditLog.setUserId(user.getId());
                auditLog.setEntityId(user.getId());
            });

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setRequestMethod("POST");
                auditLog.setRequestUrl("/api/profile/change-password");
            }

            auditLogRepository.save(auditLog);
            log.info("Password change logged: {} - {}", username, success ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            log.error("Failed to log password change", e);
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}

