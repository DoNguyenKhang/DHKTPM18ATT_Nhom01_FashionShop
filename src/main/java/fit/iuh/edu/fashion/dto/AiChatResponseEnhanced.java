package fit.iuh.edu.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enhanced AI Chat Response with Product Integration
 * Support for displaying products directly in chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseEnhanced {

    /**
     * AI text response
     */
    private String response;

    /**
     * Model used to generate response
     */
    private String model;

    /**
     * Response timestamp
     */
    private Long timestamp;

    /**
     * List of recommended products
     */
    private List<ProductCardDTO> products;

    /**
     * Intent type detected
     */
    private String intentType;

    /**
     * Confidence score (0-1)
     */
    private Double confidence;

    /**
     * Suggested follow-up questions
     */
    private List<String> suggestions;

    /**
     * Quick actions for user
     */
    private List<QuickAction> quickActions;

    /**
     * Additional metadata
     */
    private ResponseMetadata metadata;

    /**
     * Quick Action DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickAction {
        private String id;
        private String label;
        private String icon;
        private String action;
        private Object payload;
    }

    /**
     * Response Metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetadata {
        private Integer totalProducts;
        private String category;
        private String brand;
        private PriceRange priceRange;
        private List<String> filters;
    }

    /**
     * Price Range
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceRange {
        private Double min;
        private Double max;
    }
}

