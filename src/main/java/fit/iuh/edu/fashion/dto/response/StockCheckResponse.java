package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCheckResponse {
    private Long variantId;
    private String productName;
    private String colorName;
    private String sizeName;
    private Integer availableStock;
    private Boolean isAvailable;
    private String message;
}

