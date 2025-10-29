package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.models.Coupon;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.CouponRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import fit.iuh.edu.fashion.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    @GetMapping("/validate")
    public ResponseEntity<?> validateCoupon(
            @RequestParam String code,
            @RequestParam BigDecimal orderAmount
    ) {
        try {
            // Find valid coupon
            Coupon coupon = couponRepository.findValidCouponByCode(code.toUpperCase(), LocalDateTime.now())
                    .orElseThrow(() -> new RuntimeException("Mã giảm giá không tồn tại hoặc đã hết hạn"));

            // Check minimum order amount
            if (coupon.getMinOrderAmount() != null && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Đơn hàng chưa đủ giá trị tối thiểu " + formatCurrency(coupon.getMinOrderAmount()));
                return ResponseEntity.badRequest().body(error);
            }

            // Check usage limit
            if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Mã giảm giá đã hết lượt sử dụng");
                return ResponseEntity.badRequest().body(error);
            }

            // Return coupon info
            return ResponseEntity.ok(coupon);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Admin endpoints
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<Page<Coupon>> getAllCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return ResponseEntity.ok(couponRepository.findAll(pageable));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<List<Coupon>> getActiveCoupons() {
        return ResponseEntity.ok(couponRepository.findActiveCoupons(LocalDateTime.now()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> getCouponById(@PathVariable Long id) {
        return couponRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> createCoupon(
            @RequestBody Coupon coupon,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        try {
            // Check if code already exists
            if (couponRepository.findByCode(coupon.getCode().toUpperCase()).isPresent()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Mã giảm giá đã tồn tại");
                return ResponseEntity.badRequest().body(error);
            }

            // Set code to uppercase
            coupon.setCode(coupon.getCode().toUpperCase());

            // Set default values
            if (coupon.getUsedCount() == null) {
                coupon.setUsedCount(0);
            }
            if (coupon.getIsActive() == null) {
                coupon.setIsActive(true);
            }

            // Set creator
            User creator = userRepository.findById(userDetails.getId()).orElse(null);
            coupon.setCreatedBy(creator);

            // Validate dates
            if (coupon.getStartAt().isAfter(coupon.getEndAt())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Ngày bắt đầu phải trước ngày kết thúc");
                return ResponseEntity.badRequest().body(error);
            }

            // Validate value
            if (coupon.getType() == Coupon.CouponType.PERCENT) {
                if (coupon.getValue().compareTo(BigDecimal.ZERO) <= 0 ||
                        coupon.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Giá trị phần trăm phải từ 0 đến 100");
                    return ResponseEntity.badRequest().body(error);
                }
            } else {
                if (coupon.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Giá trị giảm giá phải lớn hơn 0");
                    return ResponseEntity.badRequest().body(error);
                }
            }

            Coupon savedCoupon = couponRepository.save(coupon);
            return ResponseEntity.ok(savedCoupon);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> updateCoupon(
            @PathVariable Long id,
            @RequestBody Coupon coupon
    ) {
        try {
            Coupon existingCoupon = couponRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mã giảm giá không tồn tại"));

            // Check if code is changed and already exists
            if (!existingCoupon.getCode().equals(coupon.getCode().toUpperCase())) {
                if (couponRepository.findByCode(coupon.getCode().toUpperCase()).isPresent()) {
                    Map<String, String> error = new HashMap<>();
                    error.put("message", "Mã giảm giá đã tồn tại");
                    return ResponseEntity.badRequest().body(error);
                }
            }

            // Update fields
            existingCoupon.setCode(coupon.getCode().toUpperCase());
            existingCoupon.setType(coupon.getType());
            existingCoupon.setValue(coupon.getValue());
            existingCoupon.setMaxDiscount(coupon.getMaxDiscount());
            existingCoupon.setMinOrderAmount(coupon.getMinOrderAmount());
            existingCoupon.setStartAt(coupon.getStartAt());
            existingCoupon.setEndAt(coupon.getEndAt());
            existingCoupon.setUsageLimit(coupon.getUsageLimit());
            existingCoupon.setPerUserLimit(coupon.getPerUserLimit());
            existingCoupon.setIsActive(coupon.getIsActive());

            // Validate dates
            if (existingCoupon.getStartAt().isAfter(existingCoupon.getEndAt())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Ngày bắt đầu phải trước ngày kết thúc");
                return ResponseEntity.badRequest().body(error);
            }

            Coupon updatedCoupon = couponRepository.save(existingCoupon);
            return ResponseEntity.ok(updatedCoupon);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCoupon(@PathVariable Long id) {
        try {
            if (!couponRepository.existsById(id)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "Mã giảm giá không tồn tại");
                return ResponseEntity.notFound().build();
            }
            couponRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_SALES')")
    public ResponseEntity<?> toggleCouponStatus(@PathVariable Long id) {
        try {
            Coupon coupon = couponRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Mã giảm giá không tồn tại"));

            coupon.setIsActive(!coupon.getIsActive());
            Coupon updatedCoupon = couponRepository.save(coupon);
            return ResponseEntity.ok(updatedCoupon);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "Có lỗi xảy ra: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        return String.format("%,d ₫", amount.longValue());
    }
}
