package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.OrderRequest;
import fit.iuh.edu.fashion.dto.response.OrderItemResponse;
import fit.iuh.edu.fashion.dto.response.OrderResponse;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AuditService auditService;
    private final ProductService productService;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findByCustomerId(userId, pageable)
                .map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(Order.OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable)
                .map(this::mapToOrderResponse);
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Create order
        Order order = Order.builder()
                .code(generateOrderCode())
                .customer(customer)
                .status(Order.OrderStatus.PENDING)
                .shipName(request.getShipName())
                .shipPhone(request.getShipPhone())
                .shipLine1(request.getShipLine1())
                .shipLine2(request.getShipLine2())
                .shipWard(request.getShipWard())
                .shipDistrict(request.getShipDistrict())
                .shipCity(request.getShipCity())
                .shipCountry(request.getShipCountry())
                .note(request.getNote())
                .paymentMethod(Order.PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()))
                .paymentStatus(Order.PaymentStatus.UNPAID)
                .items(new ArrayList<>())
                .build();

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var itemRequest : request.getItems()) {
            // Use pessimistic lock to prevent race condition
            ProductVariant variant = productVariantRepository.findByIdWithLock(itemRequest.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Product variant not found: " + itemRequest.getVariantId()));

            // Atomic decrease stock - prevents overselling
            int rowsAffected = productVariantRepository.decreaseStock(variant.getId(), itemRequest.getQuantity());

            if (rowsAffected == 0) {
                // Stock insufficient or variant not found
                throw new RuntimeException("Insufficient stock for product: " + variant.getProduct().getName() +
                        " (Available: " + variant.getStock() + ", Requested: " + itemRequest.getQuantity() + ")");
            }

            // Refresh variant to get updated stock
            productVariantRepository.flush();
            variant = productVariantRepository.findById(variant.getId())
                    .orElseThrow(() -> new RuntimeException("Product variant not found"));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(variant.getProduct())
                    .variant(variant)
                    .sku(variant.getSku())
                    .productName(variant.getProduct().getName())
                    .colorName(variant.getColor() != null ? variant.getColor().getName() : null)
                    .sizeName(variant.getSize() != null ? variant.getSize().getName() : null)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(variant.getPrice())
                    .discountAmount(BigDecimal.ZERO)
                    .lineTotal(variant.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())))
                    .build();

            order.getItems().add(orderItem);
            subtotal = subtotal.add(orderItem.getLineTotal());


            // Check and update stock status (auto-deactivate if out of stock)
            productService.checkAndUpdateStockStatus(variant.getId());
        }

        order.setSubtotal(subtotal);

        // Apply coupon if provided
        BigDecimal discountTotal = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isEmpty()) {
            Coupon coupon = couponRepository.findValidCouponByCode(request.getCouponCode(), LocalDateTime.now())
                    .orElseThrow(() -> new RuntimeException("Invalid or expired coupon"));

            // Check min order amount
            if (coupon.getMinOrderAmount() != null && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
                throw new RuntimeException("Order amount does not meet minimum requirement for coupon");
            }

            if (coupon.getType() == Coupon.CouponType.PERCENT) {
                discountTotal = subtotal.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscount() != null && discountTotal.compareTo(coupon.getMaxDiscount()) > 0) {
                    discountTotal = coupon.getMaxDiscount();
                }
            } else {
                discountTotal = coupon.getValue();
            }

            order.setCouponCode(request.getCouponCode());
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }

        order.setDiscountTotal(discountTotal);

        // Apply loyalty points if customer wants to use
        BigDecimal loyaltyPointsDiscount = BigDecimal.ZERO;
        int pointsUsed = 0;
        if (request.getLoyaltyPointsToUse() != null && request.getLoyaltyPointsToUse() > 0) {
            // Get customer profile
            CustomerProfile customerProfile = customerProfileRepository.findById(customer.getId())
                    .orElse(null);

            if (customerProfile != null) {
                int availablePoints = customerProfile.getLoyaltyPoint();
                pointsUsed = Math.min(request.getLoyaltyPointsToUse(), availablePoints);

                if (pointsUsed > 0) {
                    // 1 điểm = 1000 VND giảm giá
                    loyaltyPointsDiscount = BigDecimal.valueOf(pointsUsed).multiply(BigDecimal.valueOf(1000));

                    // Giảm giá không được vượt quá subtotal - discountTotal
                    BigDecimal maxPointsDiscount = subtotal.subtract(discountTotal);
                    if (loyaltyPointsDiscount.compareTo(maxPointsDiscount) > 0) {
                        loyaltyPointsDiscount = maxPointsDiscount;
                        pointsUsed = loyaltyPointsDiscount.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).intValue();
                    }

                    // Trừ điểm của khách hàng
                    customerProfile.setLoyaltyPoint(availablePoints - pointsUsed);
                    customerProfileRepository.save(customerProfile);

                    order.setLoyaltyPointsUsed(pointsUsed);
                }
            }
        }

        order.setShippingFee(BigDecimal.ZERO); // Can be calculated based on address
        order.setTaxTotal(BigDecimal.ZERO);

        // Grand total = subtotal - discount - loyalty points discount + shipping + tax
        BigDecimal grandTotal = subtotal
                .subtract(discountTotal)
                .subtract(loyaltyPointsDiscount)
                .add(order.getShippingFee())
                .add(order.getTaxTotal());

        // Đảm bảo grand total không âm
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        order.setGrandTotal(grandTotal);

        // Tính điểm tích lũy được (1% của grand total, làm tròn xuống)
        // Chỉ tính khi đơn hàng có giá trị
        if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
            int pointsEarned = grandTotal.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue();
            order.setLoyaltyPointsEarned(pointsEarned);
        }

        order = orderRepository.save(order);

        // Tạo bản ghi thanh toán cho đơn hàng COD
        try {
            if (order.getPaymentMethod() == Order.PaymentMethod.COD) {
                paymentService.createCODPayment(order);
            }
        } catch (Exception e) {
            // Log lỗi nhưng không làm gián đoạn việc tạo đơn hàng
            System.err.println("Failed to create payment record for COD order: " + e.getMessage());
        }

        // Create inventory movements
        for (OrderItem item : order.getItems()) {
            InventoryMovement movement = InventoryMovement.builder()
                    .variant(item.getVariant())
                    .quantity(-item.getQuantity())
                    .reason(InventoryMovement.MovementReason.SALE)
                    .relatedOrder(order)
                    .note("Order: " + order.getCode())
                    .createdBy(customer)
                    .build();
            inventoryMovementRepository.save(movement);
        }

        // Clear user's cart after successful order
        cartRepository.findByCustomer(customer).ifPresent(cart -> {
            cartItemRepository.deleteAll(cart.getItems());
            cart.getItems().clear();
            cartRepository.save(cart);
        });

        // Audit log
        auditService.logAction("CREATE", "Order", order.getId(), null,
                String.format("Created order: %s, Total: %s", order.getCode(), order.getGrandTotal()));

        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);

        // Tự động cập nhật trạng thái thanh toán khi hoàn thành đơn hàng
        if (status == Order.OrderStatus.COMPLETED) {
            // Khi hoàn thành đơn hàng, đánh dấu đã thanh toán (cho cả COD và các phương thức khác)
            if (order.getPaymentStatus() != Order.PaymentStatus.PAID) {
                order.setPaymentStatus(Order.PaymentStatus.PAID);
                order.setPaymentTime(LocalDateTime.now());

                // Cập nhật Payment record nếu là COD
                try {
                    if (order.getPaymentMethod() == Order.PaymentMethod.COD) {
                        var payments = paymentService.getPaymentsByOrder(order.getId());
                        for (var payment : payments) {
                            if (payment.getStatus() == fit.iuh.edu.fashion.models.Payment.PaymentStatus.PENDING) {
                                paymentService.updatePaymentStatus(payment.getId(),
                                    fit.iuh.edu.fashion.models.Payment.PaymentStatus.COMPLETED);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to update payment status: " + e.getMessage());
                }
            }

            // Cộng điểm tích lũy cho khách hàng khi hoàn thành đơn hàng
            if (order.getLoyaltyPointsEarned() > 0 && oldStatus != Order.OrderStatus.COMPLETED) {
                CustomerProfile customerProfile = customerProfileRepository.findById(order.getCustomer().getId())
                        .orElse(null);

                if (customerProfile != null) {
                    int currentPoints = customerProfile.getLoyaltyPoint();
                    customerProfile.setLoyaltyPoint(currentPoints + order.getLoyaltyPointsEarned());
                    customerProfileRepository.save(customerProfile);
                }
            }
        } else if (status == Order.OrderStatus.CANCELLED || status == Order.OrderStatus.REFUNDED) {
            // Hoàn lại kho hàng khi hủy/hoàn tiền
            restoreOrderStock(order);

            // Khi hủy đơn, đánh dấu thất bại nếu chưa thanh toán
            if (order.getPaymentStatus() == Order.PaymentStatus.UNPAID) {
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
            } else if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                // Nếu đã thanh toán -> đánh dấu REFUNDED và cập nhật Payment records
                order.setPaymentStatus(Order.PaymentStatus.REFUNDED);

                // Cập nhật Payment records
                try {
                    List<Payment> payments = paymentService.getPaymentsByOrder(order.getId());
                    for (Payment payment : payments) {
                        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                            paymentService.updatePaymentStatus(payment.getId(), Payment.PaymentStatus.REFUNDED);
                        }
                    }
                    log.info("Updated payment records to REFUNDED for order {}", order.getCode());
                } catch (Exception e) {
                    log.error("Failed to update payment records for order {}", order.getId(), e);
                }
            }

            // Hoàn lại điểm đã sử dụng khi hủy/hoàn trả đơn
            if (order.getLoyaltyPointsUsed() > 0) {
                CustomerProfile customerProfile = customerProfileRepository.findById(order.getCustomer().getId())
                        .orElse(null);

                if (customerProfile != null) {
                    int currentPoints = customerProfile.getLoyaltyPoint();
                    customerProfile.setLoyaltyPoint(currentPoints + order.getLoyaltyPointsUsed());
                    customerProfileRepository.save(customerProfile);
                    log.info("Restored {} loyalty points to customer {}",
                            order.getLoyaltyPointsUsed(), order.getCustomer().getId());
                }
            }

            // Trừ điểm đã được cộng nếu đơn từng hoàn thành
            if (oldStatus == Order.OrderStatus.COMPLETED && order.getLoyaltyPointsEarned() > 0) {
                CustomerProfile customerProfile = customerProfileRepository.findById(order.getCustomer().getId())
                        .orElse(null);

                if (customerProfile != null) {
                    int currentPoints = customerProfile.getLoyaltyPoint();
                    customerProfile.setLoyaltyPoint(Math.max(0, currentPoints - order.getLoyaltyPointsEarned()));
                    customerProfileRepository.save(customerProfile);
                    log.info("Deducted {} earned loyalty points from customer {}",
                            order.getLoyaltyPointsEarned(), order.getCustomer().getId());
                }
            }
        }

        order = orderRepository.save(order);

        // Audit log
        auditService.logAction("UPDATE_STATUS", "Order", order.getId(),
                "Status: " + oldStatus, "Status: " + status);

        return mapToOrderResponse(order);
    }

    @Transactional
    public OrderResponse updatePaymentMethod(Long orderId, String paymentMethod, Long userId) {
        log.info("Updating payment method for order {} to {} by user {}", orderId, paymentMethod, userId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        log.debug("Order status: {}, Payment status: {}, Current method: {}",
                order.getStatus(), order.getPaymentStatus(), order.getPaymentMethod());

        // Verify ownership
        if (!order.getCustomer().getId().equals(userId)) {
            log.warn("User {} attempted to update order {} owned by user {}",
                    userId, orderId, order.getCustomer().getId());
            throw new RuntimeException("You can only update your own orders");
        }

        // Only allow changing payment method for PENDING or CONFIRMED orders
        if (order.getStatus() != Order.OrderStatus.PENDING && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            log.warn("Cannot change payment method for order {} in status {}", orderId, order.getStatus());
            throw new RuntimeException("Cannot change payment method for orders in " + order.getStatus() + " status");
        }

        // Only allow changing if current payment is unpaid
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            log.warn("Cannot change payment method for order {} - already paid", orderId);
            throw new RuntimeException("Cannot change payment method for already paid orders");
        }

        // Validate payment method
        Order.PaymentMethod oldMethod = order.getPaymentMethod();
        Order.PaymentMethod newMethod;
        try {
            newMethod = Order.PaymentMethod.valueOf(paymentMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid payment method: {}", paymentMethod);
            throw new RuntimeException("Invalid payment method: " + paymentMethod);
        }

        log.info("Changing payment method from {} to {} for order {}", oldMethod, newMethod, order.getCode());

        // Cancel old pending payments when changing payment method
        try {
            List<Payment> oldPayments = paymentService.getPaymentsByOrder(order.getId());
            log.debug("Found {} payments for order {}", oldPayments.size(), order.getId());

            for (Payment payment : oldPayments) {
                // Chỉ cancel payment đang PENDING
                if (payment.getStatus() == Payment.PaymentStatus.PENDING) {
                    paymentService.updatePaymentStatus(payment.getId(), Payment.PaymentStatus.CANCELLED);
                    log.info("Cancelled old payment {} when changing from {} to {}",
                            payment.getId(), oldMethod, newMethod);
                }
            }
        } catch (Exception e) {
            log.error("Failed to cancel old payments for order: {}", order.getId(), e);
            // Continue anyway - không block việc chuyển payment method
        }

        // Update payment method
        order.setPaymentMethod(newMethod);
        order = orderRepository.save(order);

        log.info("Successfully updated payment method for order {} to {}", order.getCode(), newMethod);

        // Audit log
        auditService.logAction("UPDATE_PAYMENT_METHOD", "Order", order.getId(),
                "Payment Method: " + oldMethod, "Payment Method: " + newMethod);

        return mapToOrderResponse(order);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getCustomer().getId().equals(userId)) {
            throw new RuntimeException("You can only cancel your own orders");
        }

        // Chỉ cho phép hủy đơn PENDING hoặc CONFIRMED (chưa đóng gói)
        if (order.getStatus() != Order.OrderStatus.PENDING && order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new RuntimeException("Chỉ có thể hủy đơn hàng đang chờ xử lý hoặc đã xác nhận");
        }

        // Kiểm tra nếu đã thanh toán -> yêu cầu xử lý hoàn tiền
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            // Đánh dấu đơn hàng chờ hoàn tiền
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setPaymentStatus(Order.PaymentStatus.REFUNDED);

            // Cập nhật các payment records
            List<Payment> payments = paymentService.getPaymentsByOrder(orderId);
            for (Payment payment : payments) {
                if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                    paymentService.updatePaymentStatus(payment.getId(), Payment.PaymentStatus.REFUNDED);
                }
            }

            log.info("Order {} cancelled with refund required. Payment method: {}",
                    order.getCode(), order.getPaymentMethod());
        } else {
            // Chưa thanh toán -> hủy bình thường
            order.setStatus(Order.OrderStatus.CANCELLED);
            if (order.getPaymentStatus() == Order.PaymentStatus.UNPAID) {
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
            }
        }

        // Restore stock atomically
        restoreOrderStock(order);

        // Hoàn lại điểm tích lũy đã sử dụng
        if (order.getLoyaltyPointsUsed() > 0) {
            CustomerProfile customerProfile = customerProfileRepository.findById(userId)
                    .orElse(null);

            if (customerProfile != null) {
                int currentPoints = customerProfile.getLoyaltyPoint();
                customerProfile.setLoyaltyPoint(currentPoints + order.getLoyaltyPointsUsed());
                customerProfileRepository.save(customerProfile);
                log.info("Restored {} loyalty points to customer {}",
                        order.getLoyaltyPointsUsed(), userId);
            }
        }

        orderRepository.save(order);

        // Audit log
        auditService.logAction("CANCEL", "Order", order.getId(),
                "Status: " + order.getStatus(), "Status: CANCELLED");
    }

    /**
     * Phương thức hoàn tiền cho đơn hàng (dành cho Admin)
     */
    @Transactional
    public void processRefund(Long orderId, String adminReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Kiểm tra đơn hàng đã được thanh toán chưa
        if (order.getPaymentStatus() != Order.PaymentStatus.PAID) {
            throw new RuntimeException("Chỉ có thể hoàn tiền cho đơn hàng đã thanh toán");
        }

        // Cập nhật trạng thái đơn hàng
        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.REFUNDED);
        order.setPaymentStatus(Order.PaymentStatus.REFUNDED);

        // Cập nhật payment records
        List<Payment> payments = paymentService.getPaymentsByOrder(orderId);
        for (Payment payment : payments) {
            if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                paymentService.updatePaymentStatus(payment.getId(), Payment.PaymentStatus.REFUNDED);
            }
        }

        // Hoàn lại kho hàng
        restoreOrderStock(order);

        // Hoàn lại điểm tích lũy đã sử dụng
        if (order.getLoyaltyPointsUsed() > 0) {
            CustomerProfile customerProfile = customerProfileRepository.findById(order.getCustomer().getId())
                    .orElse(null);
            if (customerProfile != null) {
                int currentPoints = customerProfile.getLoyaltyPoint();
                customerProfile.setLoyaltyPoint(currentPoints + order.getLoyaltyPointsUsed());
                customerProfileRepository.save(customerProfile);
                log.info("Restored {} loyalty points to customer {}",
                        order.getLoyaltyPointsUsed(), order.getCustomer().getId());
            }
        }

        // Trừ lại điểm đã được cộng nếu đơn từng hoàn thành
        if (oldStatus == Order.OrderStatus.COMPLETED && order.getLoyaltyPointsEarned() > 0) {
            CustomerProfile customerProfile = customerProfileRepository.findById(order.getCustomer().getId())
                    .orElse(null);
            if (customerProfile != null) {
                int currentPoints = customerProfile.getLoyaltyPoint();
                customerProfile.setLoyaltyPoint(Math.max(0, currentPoints - order.getLoyaltyPointsEarned()));
                customerProfileRepository.save(customerProfile);
                log.info("Deducted {} earned loyalty points from customer {}",
                        order.getLoyaltyPointsEarned(), order.getCustomer().getId());
            }
        }

        orderRepository.save(order);

        // Audit log
        auditService.logAction("REFUND", "Order", order.getId(),
                "Status: " + oldStatus + ", Payment: PAID",
                "Status: REFUNDED, Payment: REFUNDED, Reason: " + adminReason);

        log.info("Refund processed for order {} by admin. Reason: {}", order.getCode(), adminReason);
    }

    /**
     * Helper method để hoàn lại kho hàng
     */
    private void restoreOrderStock(Order order) {
        for (OrderItem item : order.getItems()) {
            // Atomic increase stock when cancelling order
            int rowsAffected = productVariantRepository.increaseStock(
                    item.getVariant().getId(), item.getQuantity());

            if (rowsAffected == 0) {
                throw new RuntimeException("Failed to restore stock for variant: " +
                        item.getVariant().getId());
            }

            productVariantRepository.flush();

            // Check and update stock status (might reactivate if stock is restored)
            productService.checkAndUpdateStockStatus(item.getVariant().getId());

            // Create inventory movement
            InventoryMovement movement = InventoryMovement.builder()
                    .variant(item.getVariant())
                    .quantity(item.getQuantity())
                    .reason(InventoryMovement.MovementReason.RETURN)
                    .relatedOrder(order)
                    .note("Order " + order.getStatus() + ": " + order.getCode())
                    .build();
            inventoryMovementRepository.save(movement);
        }
    }

    private String generateOrderCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "ORD-" + timestamp;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .code(order.getCode())
                .status(order.getStatus().name())
                .subtotal(order.getSubtotal())
                .discountTotal(order.getDiscountTotal())
                .shippingFee(order.getShippingFee())
                .taxTotal(order.getTaxTotal())
                .grandTotal(order.getGrandTotal())
                .note(order.getNote())
                .placedAt(order.getPlacedAt())
                .shipName(order.getShipName())
                .shipPhone(order.getShipPhone())
                .shipLine1(order.getShipLine1())
                .shipLine2(order.getShipLine2())
                .shipWard(order.getShipWard())
                .shipDistrict(order.getShipDistrict())
                .shipCity(order.getShipCity())
                .shipCountry(order.getShipCountry())
                .couponCode(order.getCouponCode())
                .loyaltyPointsUsed(order.getLoyaltyPointsUsed())
                .loyaltyPointsEarned(order.getLoyaltyPointsEarned())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .paymentTransactionId(order.getPaymentTransactionId())
                .paymentTime(order.getPaymentTime())
                .items(order.getItems().stream()
                        .map(this::mapToOrderItemResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .productName(item.getProductName())
                .colorName(item.getColorName())
                .sizeName(item.getSizeName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .lineTotal(item.getLineTotal())
                .build();
    }
}
