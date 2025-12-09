package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthResponse {

    // System Info
    private String javaVersion;
    private String osName;
    private String osVersion;
    private String osArch;
    private long uptime; // milliseconds

    // Memory Info
    private long totalMemory;
    private long freeMemory;
    private long usedMemory;
    private long maxMemory;
    private double memoryUsagePercent;

    // CPU Info
    private int availableProcessors;
    private double systemCpuLoad;
    private double processCpuLoad;

    // Thread Info
    private int threadCount;
    private int peakThreadCount;
    private long totalStartedThreadCount;

    // Database Stats
    private Map<String, Long> databaseStats;

    // Application Stats
    private long totalUsers;
    private long totalOrders;
    private long totalProducts;
    private long todayOrders;
    private long activeUsers; // users active in last 24h

    // Disk Info
    private long totalDiskSpace;
    private long freeDiskSpace;
    private long usableDiskSpace;
    private double diskUsagePercent;

    // Status
    private String status; // HEALTHY, WARNING, CRITICAL
    private String message;
}

