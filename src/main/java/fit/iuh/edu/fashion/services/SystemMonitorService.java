package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.response.SystemHealthResponse;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMonitorService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final PaymentRepository paymentRepository;

    private final long startTime = System.currentTimeMillis();

    public SystemHealthResponse getSystemHealth() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            // Disk info
            File disk = new File("/");
            long totalDiskSpace = disk.getTotalSpace();
            long freeDiskSpace = disk.getFreeSpace();
            long usableDiskSpace = disk.getUsableSpace();
            double diskUsagePercent = (double) (totalDiskSpace - freeDiskSpace) / totalDiskSpace * 100;

            // Database stats
            Map<String, Long> dbStats = new HashMap<>();
            dbStats.put("users", userRepository.count());
            dbStats.put("orders", orderRepository.count());
            dbStats.put("products", productRepository.count());
            dbStats.put("auditLogs", auditLogRepository.count());
            dbStats.put("payments", paymentRepository.count());

            // Application stats
            long totalUsers = userRepository.count();
            long totalOrders = orderRepository.count();
            long totalProducts = productRepository.count();

            // Today's orders
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayOrders = orderRepository.countByPlacedAtAfter(startOfDay);

            // Active users (logged in last 24 hours)
            LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
            long activeUsers = auditLogRepository.countDistinctUsersByActionAndCreatedAtAfter("LOGIN", last24Hours);

            // Determine system status
            String status = "HEALTHY";
            String message = "Hệ thống hoạt động bình thường";

            if (memoryUsagePercent > 90 || diskUsagePercent > 90) {
                status = "CRITICAL";
                message = "Cảnh báo: Tài nguyên hệ thống gần cạn kiệt!";
            } else if (memoryUsagePercent > 75 || diskUsagePercent > 75) {
                status = "WARNING";
                message = "Cảnh báo: Tài nguyên hệ thống đang cao";
            }

            return SystemHealthResponse.builder()
                    // System Info
                    .javaVersion(System.getProperty("java.version"))
                    .osName(System.getProperty("os.name"))
                    .osVersion(System.getProperty("os.version"))
                    .osArch(System.getProperty("os.arch"))
                    .uptime(System.currentTimeMillis() - startTime)
                    // Memory Info
                    .totalMemory(totalMemory)
                    .freeMemory(freeMemory)
                    .usedMemory(usedMemory)
                    .maxMemory(maxMemory)
                    .memoryUsagePercent(memoryUsagePercent)
                    // CPU Info
                    .availableProcessors(osBean.getAvailableProcessors())
                    .systemCpuLoad(osBean.getSystemLoadAverage())
                    // Thread Info
                    .threadCount(threadBean.getThreadCount())
                    .peakThreadCount(threadBean.getPeakThreadCount())
                    .totalStartedThreadCount(threadBean.getTotalStartedThreadCount())
                    // Database Stats
                    .databaseStats(dbStats)
                    // Application Stats
                    .totalUsers(totalUsers)
                    .totalOrders(totalOrders)
                    .totalProducts(totalProducts)
                    .todayOrders(todayOrders)
                    .activeUsers(activeUsers)
                    // Disk Info
                    .totalDiskSpace(totalDiskSpace)
                    .freeDiskSpace(freeDiskSpace)
                    .usableDiskSpace(usableDiskSpace)
                    .diskUsagePercent(diskUsagePercent)
                    // Status
                    .status(status)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get system health", e);
            return SystemHealthResponse.builder()
                    .status("ERROR")
                    .message("Không thể lấy thông tin hệ thống: " + e.getMessage())
                    .build();
        }
    }
}

