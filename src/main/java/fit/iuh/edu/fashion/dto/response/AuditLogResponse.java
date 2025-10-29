package fit.iuh.edu.fashion.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    private String userAgent;
    private String requestMethod;
    private String requestUrl;
    private String oldValue;
    private String newValue;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}

