package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.models.AuditLog;
import fit.iuh.edu.fashion.repositories.AuditLogRepository;
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

    @Async
    public void logAction(String action, String entityType, Long entityId, String oldValue, String newValue) {
        try {
            HttpServletRequest request = getCurrentRequest();
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

