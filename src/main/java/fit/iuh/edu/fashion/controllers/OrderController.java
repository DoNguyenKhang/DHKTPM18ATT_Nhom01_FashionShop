package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.OrderRequest;
import fit.iuh.edu.fashion.dto.response.OrderResponse;
import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.security.CustomUserDetails;
import fit.iuh.edu.fashion.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Sort sort = Sort.by(Sort.Direction.DESC, "placedAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(orderService.getMyOrders(userDetails.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(orderService.createOrder(userDetails.getId(), request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        orderService.cancelOrder(id, userDetails.getId());
        return ResponseEntity.ok().build();
    }

    // Admin/Staff endpoints
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Sort sort = Sort.by(Sort.Direction.DESC, "placedAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable Order.OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(orderService.getOrdersByStatus(status, pageable));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    @PutMapping("/{id}/payment-method")
    public ResponseEntity<?> updatePaymentMethod(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            String paymentMethod = request.get("paymentMethod");
            if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Payment method is required"));
            }

            OrderResponse order = orderService.updatePaymentMethod(id, paymentMethod, userDetails.getId());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> processRefund(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {
        try {
            String reason = request.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "message", "Vui lòng nhập lý do hoàn tiền"
                ));
            }

            orderService.processRefund(id, reason);
            return ResponseEntity.ok(Map.of(
                "message", "Xử lý hoàn tiền thành công",
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", e.getMessage(),
                "success", false
            ));
        }
    }
}

