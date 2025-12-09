package fit.iuh.edu.fashion.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @JsonIgnoreProperties({"customerProfile", "employeeProfile", "roles", "hibernateLazyInitializer", "handler"})
    @ManyToOne
    @JoinColumn(name = "customer_user_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(length = 255)
    private String note;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Shipping snapshot
    @Column(name = "ship_name", nullable = false, length = 160)
    private String shipName;

    @Column(name = "ship_phone", nullable = false, length = 32)
    private String shipPhone;

    @Column(name = "ship_line1", nullable = false, length = 255)
    private String shipLine1;

    @Column(name = "ship_line2", length = 255)
    private String shipLine2;

    @Column(name = "ship_ward", length = 128)
    private String shipWard;

    @Column(name = "ship_district", length = 128)
    private String shipDistrict;

    @Column(name = "ship_city", nullable = false, length = 128)
    private String shipCity;

    @Column(name = "ship_country", nullable = false, length = 64)
    private String shipCountry = "Vietnam";

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Builder.Default
    @Column(name = "loyalty_points_used", nullable = false)
    private Integer loyaltyPointsUsed = 0;

    @Builder.Default
    @Column(name = "loyalty_points_earned", nullable = false)
    private Integer loyaltyPointsEarned = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    @JsonIgnoreProperties({"order", "hibernateLazyInitializer", "handler"})
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        placedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        PENDING, CONFIRMED, PACKING, SHIPPING, COMPLETED, CANCELLED, REFUNDED
    }

    public enum PaymentMethod {
        COD, VNPAY, MOMO, ZALOPAY
    }

    public enum PaymentStatus {
        UNPAID, PAID, REFUNDED, FAILED
    }
}
