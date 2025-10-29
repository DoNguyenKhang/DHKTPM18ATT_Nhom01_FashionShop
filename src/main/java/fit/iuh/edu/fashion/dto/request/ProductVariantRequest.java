package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantRequest {
    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be positive")
    private Long productId;

    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 100, message = "SKU must be between 3 and 100 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "SKU must contain only uppercase letters, numbers and hyphens")
    private String sku;

    @Positive(message = "Color ID must be positive")
    private Long colorId;

    @Positive(message = "Size ID must be positive")
    private Long sizeId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Price is too large")
    @Digits(integer = 10, fraction = 2, message = "Price format is invalid")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Compare price must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Compare price is too large")
    @Digits(integer = 10, fraction = 2, message = "Compare price format is invalid")
    private BigDecimal compareAtPrice;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    @Max(value = 999999, message = "Stock is too large")
    private Integer stock = 0;

    @Min(value = 0, message = "Weight cannot be negative")
    @Max(value = 999999, message = "Weight is too large")
    private Integer weightGram;

    @Size(max = 100, message = "Barcode must not exceed 100 characters")
    @Pattern(regexp = "^[0-9A-Z-]*$", message = "Barcode must contain only numbers, uppercase letters and hyphens")
    private String barcode;

    private Boolean isActive = true;
}
