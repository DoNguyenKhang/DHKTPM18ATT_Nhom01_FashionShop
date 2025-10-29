package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(COD|BANK_TRANSFER|VNPAY|MOMO)$",
             message = "Payment method must be one of: COD, BANK_TRANSFER, VNPAY, MOMO")
    private String paymentMethod; // COD, BANK_TRANSFER, VNPAY, MOMO

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount is too large")
    private Double amount;

    // For online payment
    @Size(max = 50, message = "Bank code must not exceed 50 characters")
    private String bankCode;

    @Size(max = 500, message = "Return URL must not exceed 500 characters")
    private String returnUrl;

    // Payment gateway response
    @Size(max = 255, message = "Transaction ID must not exceed 255 characters")
    private String transactionId;

    @Size(max = 50, message = "Response code must not exceed 50 characters")
    private String responseCode;
}
