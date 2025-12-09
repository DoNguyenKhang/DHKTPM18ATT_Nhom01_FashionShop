package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @NotEmpty(message = "Order items cannot be empty")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "Shipping name is required")
    @Size(min = 2, max = 160, message = "Shipping name must be between 2 and 160 characters")
    private String shipName;

    @NotBlank(message = "Shipping phone is required")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Invalid phone number format")
    private String shipPhone;

    @NotBlank(message = "Shipping address is required")
    @Size(min = 5, max = 255, message = "Shipping address must be between 5 and 255 characters")
    private String shipLine1;

    @Size(max = 255, message = "Shipping address line 2 must not exceed 255 characters")
    private String shipLine2;

    @Size(max = 128, message = "Ward must not exceed 128 characters")
    private String shipWard;

    @Size(max = 128, message = "District must not exceed 128 characters")
    private String shipDistrict;

    @NotBlank(message = "City is required")
    @Size(min = 2, max = 128, message = "City must be between 2 and 128 characters")
    private String shipCity;

    @Size(max = 64, message = "Country must not exceed 64 characters")
    private String shipCountry = "Vietnam";

    @Pattern(regexp = "^$|^[A-Z0-9]{4,40}$", message = "Invalid coupon code format")
    private String couponCode; // Optional - null or empty means no coupon (^$ allows empty string)

    private Integer loyaltyPointsToUse = 0; // Số điểm tích lũy khách hàng muốn sử dụng

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;

    private String paymentMethod = "COD"; // COD, VNPAY, MOMO, ZALOPAY
}
