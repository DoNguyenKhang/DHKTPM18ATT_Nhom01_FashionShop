package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.OrderRequest;
import fit.iuh.edu.fashion.dto.response.OrderItemResponse;
import fit.iuh.edu.fashion.dto.response.OrderResponse;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AuditService auditService;
    private final ProductService productService;

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
                discountTotal = subtotal.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100));
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
        order.setShippingFee(BigDecimal.ZERO); // Can be calculated based on address
        order.setTaxTotal(BigDecimal.ZERO);
        order.setGrandTotal(subtotal.subtract(discountTotal).add(order.getShippingFee()).add(order.getTaxTotal()));

        order = orderRepository.save(order);

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
            }
        } else if (status == Order.OrderStatus.CANCELLED || status == Order.OrderStatus.REFUNDED) {
            // Khi hủy đơn, đánh dấu thất bại nếu chưa thanh toán
            if (order.getPaymentStatus() == Order.PaymentStatus.UNPAID) {
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
            } else if (status == Order.OrderStatus.REFUNDED && order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
            }
        }

        order = orderRepository.save(order);

        // Audit log
        auditService.logAction("UPDATE_STATUS", "Order", order.getId(),
                "Status: " + oldStatus, "Status: " + status);

        return mapToOrderResponse(order);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getCustomer().getId().equals(userId)) {
            throw new RuntimeException("You can only cancel your own orders");
        }

        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new RuntimeException("Only pending orders can be cancelled");
        }

        // Restore stock atomically
        for (OrderItem item : order.getItems()) {
            // Atomic increase stock when cancelling order
            int rowsAffected = productVariantRepository.increaseStock(item.getVariant().getId(), item.getQuantity());

            if (rowsAffected == 0) {
                throw new RuntimeException("Failed to restore stock for variant: " + item.getVariant().getId());
            }

            productVariantRepository.flush();

            // Check and update stock status (might reactivate if stock is restored)
            // Note: Auto-reactivation is currently commented out in ProductService
            // You can enable it there if needed
            productService.checkAndUpdateStockStatus(item.getVariant().getId());

            // Create inventory movement
            InventoryMovement movement = InventoryMovement.builder()
                    .variant(item.getVariant())
                    .quantity(item.getQuantity())
                    .reason(InventoryMovement.MovementReason.RETURN)
                    .relatedOrder(order)
                    .note("Order cancelled: " + order.getCode())
                    .build();
            inventoryMovementRepository.save(movement);
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Audit log
        auditService.logAction("CANCEL", "Order", order.getId(),
                "Status: PENDING", "Status: CANCELLED");
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
