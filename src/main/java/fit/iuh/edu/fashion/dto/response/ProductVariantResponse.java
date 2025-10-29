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
public class ProductVariantResponse {
    private Long id;
    private String sku;
    private ColorResponse color;
    private SizeResponse size;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Integer stock;
    private Integer weightGram;
    private String barcode;
    private Boolean isActive;
}

