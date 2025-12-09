package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.PaymentDetailDTO;
import fit.iuh.edu.fashion.models.Payment;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.PaymentRepository;
import fit.iuh.edu.fashion.repositories.PaymentTransactionRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import fit.iuh.edu.fashion.security.JwtTokenProvider;
import fit.iuh.edu.fashion.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentManagementController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * Lấy tất cả thanh toán (Admin và Staff Sales)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<Payment> paymentPage = paymentService.getAllPayments(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("payments", paymentPage.getContent());
            response.put("currentPage", page);
            response.put("totalPages", paymentPage.getTotalPages());
            response.put("totalItems", paymentPage.getTotalElements());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy chi tiết thanh toán
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES', 'CUSTOMER')")
    public ResponseEntity<?> getPaymentById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            User currentUser = getCurrentUser(authHeader);

            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));

            // Kiểm tra quyền truy cập
            if (!isAdmin(currentUser) && !isStaffSales(currentUser)) {
                if (!payment.getOrder().getCustomer().getId().equals(currentUser.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Bạn không có quyền xem thông tin này"));
                }
            }

            // Lấy transaction liên quan nếu có
            PaymentTransaction transaction = null;
            if (payment.getTransactionId() != null) {
                transaction = paymentTransactionRepository.findByTransactionId(payment.getTransactionId())
                        .orElse(null);
            }

            // Sử dụng DTO để tránh circular reference
            PaymentDetailDTO paymentDetail = PaymentDetailDTO.fromPayment(payment, transaction);

            return ResponseEntity.ok(paymentDetail);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy thanh toán theo đơn hàng
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES', 'CUSTOMER')")
    public ResponseEntity<?> getPaymentsByOrder(
            @PathVariable Long orderId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            User currentUser = getCurrentUser(authHeader);

            List<Payment> payments = paymentService.getPaymentsByOrder(orderId);

            if (!payments.isEmpty()) {
                Payment firstPayment = payments.get(0);
                // Kiểm tra quyền truy cập
                if (!isAdmin(currentUser) && !isStaffSales(currentUser)) {
                    if (!firstPayment.getOrder().getCustomer().getId().equals(currentUser.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("message", "Bạn không có quyền xem thông tin này"));
                    }
                }
            }

            return ResponseEntity.ok(Map.of("payments", payments));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Cập nhật trạng thái thanh toán (Admin only)
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            Payment.PaymentStatus paymentStatus = Payment.PaymentStatus.valueOf(status.toUpperCase());
            Payment updatedPayment = paymentService.updatePaymentStatus(id, paymentStatus);

            return ResponseEntity.ok(Map.of(
                    "message", "Cập nhật trạng thái thành công",
                    "payment", updatedPayment
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Trạng thái không hợp lệ"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy thống kê thanh toán
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getPaymentStatistics() {
        try {
            Map<String, Object> statistics = paymentService.getPaymentStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy danh sách thanh toán theo trạng thái
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getPaymentsByStatus(@PathVariable String status) {
        try {
            Payment.PaymentStatus paymentStatus = Payment.PaymentStatus.valueOf(status.toUpperCase());
            List<Payment> payments = paymentRepository.findAll().stream()
                    .filter(p -> p.getStatus() == paymentStatus)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "status", status,
                    "count", payments.size(),
                    "payments", payments
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Trạng thái không hợp lệ"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Dashboard thống kê tổng quan
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getPaymentDashboard() {
        try {
            Map<String, Object> statistics = paymentService.getPaymentStatistics();

            // Thêm thông tin giao dịch
            List<PaymentTransaction> recentTransactions = paymentTransactionRepository.findAll()
                    .stream()
                    .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                    .limit(10)
                    .toList();

            Map<String, Object> dashboard = new HashMap<>();
            dashboard.putAll(statistics);
            dashboard.put("recentTransactions", recentTransactions);
            dashboard.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Xác nhận thanh toán COD (Khi nhân viên giao hàng nhận được tiền)
     */
    @PostMapping("/{id}/confirm-cod")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> confirmCODPayment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));

            // Kiểm tra xem có phải COD không
            if (!"COD".equalsIgnoreCase(payment.getPaymentMethod())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Chỉ có thể xác nhận thanh toán COD"));
            }

            // Kiểm tra trạng thái hiện tại
            if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Thanh toán đã được xác nhận trước đó"));
            }

            // Cập nhật trạng thái payment
            Payment updatedPayment = paymentService.updatePaymentStatus(id, Payment.PaymentStatus.COMPLETED);

            // Format payDate theo định dạng yyyyMMddHHmmss (14 ký tự) để fit với VARCHAR(20)
            String payDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Tạo PaymentTransaction để lưu lịch sử giao dịch
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .order(payment.getOrder())
                    .transactionId("COD-" + System.currentTimeMillis()) // Mã giao dịch COD
                    .txnRef(payment.getOrder().getCode()) // Mã đơn hàng
                    .amount(BigDecimal.valueOf(payment.getAmount()))
                    .paymentMethod(fit.iuh.edu.fashion.models.Order.PaymentMethod.COD)
                    .status(PaymentTransaction.TransactionStatus.SUCCESS) // Thành công
                    .responseCode("00") // Code thành công
                    .orderInfo("Thanh toán COD cho đơn hàng " + payment.getOrder().getCode())
                    .transactionType("COD_CONFIRM") // Loại giao dịch
                    .payDate(payDate) // Format: yyyyMMddHHmmss
                    .rawData(request != null && request.containsKey("note") ?
                            "{\"note\":\"" + request.get("note") + "\"}" : "{}")
                    .build();

            // Lưu transaction vào database
            paymentTransactionRepository.save(transaction);

            // Cập nhật thêm thông tin nếu có
            if (request != null && request.containsKey("note")) {
                String note = request.get("note");
                String currentInfo = updatedPayment.getPaymentInfo();
                updatedPayment.setPaymentInfo(currentInfo + "\nGhi chú: " + note);
                paymentRepository.save(updatedPayment);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Đã xác nhận thanh toán COD thành công",
                    "payment", updatedPayment,
                    "transaction", transaction
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Đánh dấu thanh toán COD thất bại (Khi không giao được hàng/không thu được tiền)
     */
    @PostMapping("/{id}/fail-cod")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> failCODPayment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            Payment payment = paymentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));

            // Kiểm tra xem có phải COD không
            if (!"COD".equalsIgnoreCase(payment.getPaymentMethod())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Chỉ có thể đánh dấu thất bại cho thanh toán COD"));
            }

            // Lấy lý do thất bại
            String failReason = request != null && request.containsKey("reason")
                    ? request.get("reason")
                    : "Không thu được tiền COD";

            // Cập nhật trạng thái payment
            Payment updatedPayment = paymentService.updatePaymentStatus(id, Payment.PaymentStatus.FAILED);

            // Format payDate theo định dạng yyyyMMddHHmmss (14 ký tự) để fit với VARCHAR(20)
            String payDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Tạo PaymentTransaction để lưu lịch sử giao dịch thất bại
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .order(payment.getOrder())
                    .transactionId("COD-FAIL-" + System.currentTimeMillis()) // Mã giao dịch COD thất bại
                    .txnRef(payment.getOrder().getCode()) // Mã đơn hàng
                    .amount(BigDecimal.valueOf(payment.getAmount()))
                    .paymentMethod(fit.iuh.edu.fashion.models.Order.PaymentMethod.COD)
                    .status(PaymentTransaction.TransactionStatus.FAILED) // Thất bại
                    .responseCode("99") // Code thất bại
                    .orderInfo("Thanh toán COD thất bại cho đơn hàng " + payment.getOrder().getCode()
                            + " - Lý do: " + failReason)
                    .transactionType("COD_FAIL") // Loại giao dịch
                    .payDate(payDate) // Format: yyyyMMddHHmmss
                    .rawData("{\"reason\":\"" + failReason + "\"}")
                    .build();

            // Lưu transaction vào database
            paymentTransactionRepository.save(transaction);

            // Cập nhật lý do thất bại vào payment info
            String currentInfo = updatedPayment.getPaymentInfo();
            updatedPayment.setPaymentInfo(currentInfo + "\nLý do thất bại: " + failReason);
            paymentRepository.save(updatedPayment);

            return ResponseEntity.ok(Map.of(
                    "message", "Đã đánh dấu thanh toán COD thất bại",
                    "payment", updatedPayment,
                    "transaction", transaction
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy danh sách thanh toán COD đang chờ xác nhận
     */
    @GetMapping("/cod/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getPendingCODPayments() {
        try {
            List<Payment> pendingCODPayments = paymentRepository.findAll().stream()
                    .filter(p -> "COD".equalsIgnoreCase(p.getPaymentMethod()))
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "count", pendingCODPayments.size(),
                    "payments", pendingCODPayments
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Thống kê thanh toán theo phương thức
     */
    @GetMapping("/statistics/by-method")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getPaymentStatisticsByMethod() {
        try {
            List<Payment> allPayments = paymentRepository.findAll();

            Map<String, Map<String, Object>> methodStats = new HashMap<>();

            // Thống kê cho mỗi phương thức
            for (Payment payment : allPayments) {
                String method = payment.getPaymentMethod();
                methodStats.putIfAbsent(method, new HashMap<>());
                Map<String, Object> stats = methodStats.get(method);

                // Đếm tổng số
                long count = (long) stats.getOrDefault("count", 0L) + 1;
                stats.put("count", count);

                // Tính tổng tiền (chỉ tính các thanh toán thành công)
                if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                    double total = (double) stats.getOrDefault("totalAmount", 0.0) + payment.getAmount();
                    stats.put("totalAmount", total);

                    long completed = (long) stats.getOrDefault("completed", 0L) + 1;
                    stats.put("completed", completed);
                }

                // Đếm pending
                if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
                    long pending = (long) stats.getOrDefault("pending", 0L) + 1;
                    stats.put("pending", pending);
                }

                // Đếm failed
                if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
                    long failed = (long) stats.getOrDefault("failed", 0L) + 1;
                    stats.put("failed", failed);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "statistics", methodStats,
                    "lastUpdated", LocalDateTime.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Đồng bộ lại trạng thái Payment và Order cho toàn bộ dữ liệu
     * API này sẽ tìm các Payment và Order có trạng thái không khớp và đồng bộ lại
     * Chỉ Admin mới có quyền thực hiện
     */
    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> syncAllPaymentOrderStatus() {
        try {
            log.info("Admin requested sync all payment-order status");
            Map<String, Object> result = paymentService.syncAllPaymentOrderStatus();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error syncing payment-order status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi khi đồng bộ: " + e.getMessage()));
        }
    }

    // Helper methods
    private User getCurrentUser(String authHeader) {
        String token = authHeader.substring(7);
        String email = jwtTokenProvider.extractUsername(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ADMIN"));
    }

    private boolean isStaffSales(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("STAFF_SALES"));
    }
}

