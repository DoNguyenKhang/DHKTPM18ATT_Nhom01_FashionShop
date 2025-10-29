package fit.iuh.edu.fashion.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "transaction_id", length = 100)
    private String transactionId; // VNPay TransactionNo

    @Column(name = "txn_ref", nullable = false, length = 100)
    private String txnRef; // Order code

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private Order.PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "response_code", length = 10)
    private String responseCode; // vnp_ResponseCode

    @Column(name = "bank_code", length = 20)
    private String bankCode; // vnp_BankCode

    @Column(name = "bank_tran_no", length = 100)
    private String bankTranNo; // vnp_BankTranNo

    @Column(name = "card_type", length = 20)
    private String cardType; // vnp_CardType

    @Column(name = "pay_date", length = 20)
    private String payDate; // vnp_PayDate

    @Column(name = "order_info", columnDefinition = "TEXT")
    private String orderInfo; // vnp_OrderInfo

    @Column(name = "transaction_type", length = 50)
    private String transactionType; // IPN or Return callback

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "secure_hash", columnDefinition = "TEXT")
    private String secureHash; // For verification

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData; // JSON of all VNPay params

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TransactionStatus {
        PENDING,    // Đang xử lý
        SUCCESS,    // Thành công
        FAILED,     // Thất bại
        CANCELLED,  // Hủy
        REFUNDED    // Hoàn tiền
    }
}

