package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.response.PaymentTransactionResponse;
import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.OrderRepository;
import fit.iuh.edu.fashion.repositories.PaymentTransactionRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import fit.iuh.edu.fashion.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payment-transactions")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * Lấy lịch sử giao dịch thanh toán của một đơn hàng
     */
    @GetMapping("/order/{orderCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER', 'STAFF_SALES')")
    public ResponseEntity<?> getTransactionsByOrderCode(
            @PathVariable String orderCode,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Kiểm tra quyền truy cập
            User currentUser = getCurrentUser(authHeader);

            Order order = orderRepository.findByCode(orderCode)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

            // Chỉ cho phép xem giao dịch của chính mình (trừ admin và staff)
            if (!isAdmin(currentUser) &&
                !isStaffSales(currentUser) &&
                !order.getCustomer().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Bạn không có quyền xem thông tin này"));
            }

            List<PaymentTransaction> transactions = paymentTransactionRepository
                    .findByOrderOrderByCreatedAtDesc(order);

            List<PaymentTransactionResponse> responses = transactions.stream()
                    .map(PaymentTransactionResponse::fromEntity)
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("orderCode", orderCode);
            result.put("totalTransactions", transactions.size());
            result.put("transactions", responses);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy tất cả giao dịch thanh toán (chỉ admin và staff)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();

            // Phân trang đơn giản
            int start = page * size;
            int end = Math.min(start + size, transactions.size());

            List<PaymentTransactionResponse> responses = transactions.subList(start, end).stream()
                    .map(PaymentTransactionResponse::fromEntity)
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("transactions", responses);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("totalTransactions", transactions.size());
            result.put("totalPages", (transactions.size() + size - 1) / size);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy chi tiết một giao dịch
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER', 'STAFF_SALES')")
    public ResponseEntity<?> getTransactionById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            User currentUser = getCurrentUser(authHeader);

            PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

            // Kiểm tra quyền truy cập
            if (!isAdmin(currentUser) &&
                !isStaffSales(currentUser) &&
                !transaction.getOrder().getCustomer().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Bạn không có quyền xem thông tin này"));
            }

            PaymentTransactionResponse response = PaymentTransactionResponse.fromEntity(transaction);

            // Thêm thông tin chi tiết
            Map<String, Object> result = new HashMap<>();
            result.put("transaction", response);
            result.put("rawData", transaction.getRawData());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    /**
     * Lấy lịch sử giao dịch của user hiện tại
     */
    @GetMapping("/my-transactions")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getMyTransactions(@RequestHeader("Authorization") String authHeader) {
        try {
            User currentUser = getCurrentUser(authHeader);

            // Lấy tất cả đơn hàng của user
            List<Order> orders = orderRepository.findByCustomerOrderByPlacedAtDesc(currentUser);

            // Lấy tất cả giao dịch từ các đơn hàng này
            List<PaymentTransaction> allTransactions = orders.stream()
                    .flatMap(order -> paymentTransactionRepository.findByOrder(order).stream())
                    .toList();

            List<PaymentTransactionResponse> responses = allTransactions.stream()
                    .map(PaymentTransactionResponse::fromEntity)
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("totalTransactions", responses.size());
            result.put("transactions", responses);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Lỗi: " + e.getMessage()));
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
