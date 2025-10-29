package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long id;
    private String code;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal discountTotal;
    private BigDecimal shippingFee;
    private BigDecimal taxTotal;
    private BigDecimal grandTotal;
    private String note;
    private LocalDateTime placedAt;

    // Shipping info
    private String shipName;
    private String shipPhone;
    private String shipLine1;
    private String shipLine2;
    private String shipWard;
    private String shipDistrict;
    private String shipCity;
    private String shipCountry;

    private String couponCode;
    private String paymentMethod;
    private String paymentStatus;
    private String paymentTransactionId;
    private LocalDateTime paymentTime;
    private List<OrderItemResponse> items;
}
