package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.response.AuditLogResponse;
import fit.iuh.edu.fashion.models.AuditLog;
import fit.iuh.edu.fashion.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getAllAuditLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findAll(pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AuditLogResponse> getAuditLogById(@PathVariable Long id) {
        return auditLogRepository.findById(id)
                .map(this::mapToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-user/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByUser(
            @PathVariable String username,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findByUsername(username, pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/by-entity/{entityType}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByEntityType(
            @PathVariable String entityType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findByEntityType(entityType, pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/by-entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        List<AuditLogResponse> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/by-action/{action}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByAction(
            @PathVariable String action,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findByAction(action, pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getFailedAuditLogs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findByStatus("FAILED", pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditLogRepository.findByCreatedAtBetween(startDate, endDate, pageable)
                .map(this::mapToResponse);
        return ResponseEntity.ok(auditLogs);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getAuditStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // Total logs
        statistics.put("totalLogs", auditLogRepository.count());

        // Logs by action
        statistics.put("loginAttempts", auditLogRepository.countByAction("LOGIN"));
        statistics.put("createActions", auditLogRepository.countByAction("CREATE"));
        statistics.put("updateActions", auditLogRepository.countByAction("UPDATE"));
        statistics.put("deleteActions", auditLogRepository.countByAction("DELETE"));

        // Failed actions
        statistics.put("failedActions", auditLogRepository.countByStatus("FAILED"));

        // Recent activity (last 24 hours)
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        statistics.put("recentActivity", auditLogRepository.countByCreatedAtAfter(last24Hours));

        return ResponseEntity.ok(statistics);
    }

    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUserId())
                .username(auditLog.getUsername())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .requestMethod(auditLog.getRequestMethod())
                .requestUrl(auditLog.getRequestUrl())
                .oldValue(auditLog.getOldValue())
                .newValue(auditLog.getNewValue())
                .status(auditLog.getStatus())
                .errorMessage(auditLog.getErrorMessage())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}

