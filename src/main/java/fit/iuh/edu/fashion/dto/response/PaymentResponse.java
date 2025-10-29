package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String paymentMethod;
    private Double amount;
    private String status; // PENDING, COMPLETED, FAILED, REFUNDED
    private String transactionId;
    private String paymentUrl; // For redirect to payment gateway
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

