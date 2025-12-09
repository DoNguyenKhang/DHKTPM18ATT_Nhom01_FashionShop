package fit.iuh.edu.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Product Card DTO for displaying in chat
 * Optimized for chatbot display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardDTO {

    /**
     * Product ID
     */
    private Long id;

    /**
     * Product name
     */
    private String name;

    /**
     * Brand name
     */
    private String brand;

    /**
     * Main image URL
     */
    private String imageUrl;

    /**
     * Current price
     */
    private Double price;

    /**
     * Original price (before discount)
     */
    private Double originalPrice;

    /**
     * Discount percentage
     */
    private Integer discount;

    /**
     * Available sizes
     */
    private List<String> availableSizes;

    /**
     * Available colors
     */
    private List<String> availableColors;

    /**
     * Stock status
     */
    private String stockStatus; // IN_STOCK, LOW_STOCK, OUT_OF_STOCK

    /**
     * Rating (1-5)
     */
    private Double rating;

    /**
     * Number of reviews
     */
    private Integer reviewCount;

    /**
     * Short description
     */
    private String shortDescription;

    /**
     * Category
     */
    private String category;

    /**
     * Is new arrival
     */
    private Boolean isNew;

    /**
     * Is on sale
     */
    private Boolean isOnSale;

    /**
     * Is bestseller
     */
    private Boolean isBestseller;

    /**
     * Tags
     */
    private List<String> tags;

    /**
     * AI recommendation score (0-100)
     */
    private Integer recommendationScore;

    /**
     * AI recommendation reason
     */
    private String recommendationReason;

    /**
     * Get formatted price
     */
    public String getFormattedPrice() {
        if (price == null) return "0đ";
        return String.format("%,.0fđ", price);
    }

    /**
     * Get formatted original price
     */
    public String getFormattedOriginalPrice() {
        if (originalPrice == null) return null;
        return String.format("%,.0fđ", originalPrice);
    }

    /**
     * Check if product has discount
     */
    public boolean hasDiscount() {
        return discount != null && discount > 0;
    }

    /**
     * Check if product is in stock
     */
    public boolean isInStock() {
        return "IN_STOCK".equals(stockStatus);
    }

    /**
     * Get stock indicator color
     */
    public String getStockColor() {
        if ("IN_STOCK".equals(stockStatus)) return "success";
        if ("LOW_STOCK".equals(stockStatus)) return "warning";
        return "danger";
    }

    /**
     * Get stock message
     */
    public String getStockMessage() {
        if ("IN_STOCK".equals(stockStatus)) return "Còn hàng";
        if ("LOW_STOCK".equals(stockStatus)) return "Sắp hết";
        return "Hết hàng";
    }
}

