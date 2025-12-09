package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.response.SystemHealthResponse;
import fit.iuh.edu.fashion.services.SystemMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(systemMonitorService.getSystemHealth());
    }
}

