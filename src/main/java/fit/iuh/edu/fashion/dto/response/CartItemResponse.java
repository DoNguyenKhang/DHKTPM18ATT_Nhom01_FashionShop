package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {
    private Long id;
    private ProductVariantResponse variant;
    private String productName;
    private String colorName;
    private String sizeName;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private Integer availableStock; // Số lượng tồn kho còn lại
    private Boolean outOfStock; // True nếu hết hàng
    private Boolean insufficientStock; // True nếu số lượng trong giỏ > tồn kho
}
