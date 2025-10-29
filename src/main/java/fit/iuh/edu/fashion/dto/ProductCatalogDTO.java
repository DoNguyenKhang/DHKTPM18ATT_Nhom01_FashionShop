package fit.iuh.edu.fashion.dto;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * DTO tối ưu cho AI context - chỉ chứa thông tin cần thiết
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCatalogDTO implements Serializable {
    private Long id;
    private String name;
    private String description;
    private String brandName;
    private List<String> categories;
    private List<VariantInfo> variants;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String material;
    private String origin;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantInfo implements Serializable {
        private String color;
        private String size;
        private BigDecimal price;
        private Integer stock;
        private boolean available;
    }

    /**
     * Tạo mô tả ngắn gọn cho AI
     */
    public String toAiDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);

        if (brandName != null) {
            sb.append(" - ").append(brandName);
        }

        if (minPrice != null) {
            if (maxPrice != null && !minPrice.equals(maxPrice)) {
                sb.append(" (").append(formatPrice(minPrice))
                  .append(" - ").append(formatPrice(maxPrice)).append(")");
            } else {
                sb.append(" (").append(formatPrice(minPrice)).append(")");
            }
        }

        if (variants != null && !variants.isEmpty()) {
            sb.append(" - Có sẵn: ");
            Set<String> colors = new java.util.HashSet<>();
            Set<String> sizes = new java.util.HashSet<>();

            for (VariantInfo v : variants) {
                if (v.available && v.stock > 0) {
                    if (v.color != null) colors.add(v.color);
                    if (v.size != null) sizes.add(v.size);
                }
            }

            if (!colors.isEmpty()) {
                sb.append("màu ").append(String.join(", ", colors));
            }
            if (!sizes.isEmpty()) {
                if (!colors.isEmpty()) sb.append("; ");
                sb.append("size ").append(String.join(", ", sizes));
            }
        }

        return sb.toString();
    }

    private String formatPrice(BigDecimal price) {
        return String.format("%,d₫", price.longValue());
    }

    /**
     * Lấy danh sách màu sắc có sẵn dưới dạng string
     */
    public String getColors() {
        if (variants == null || variants.isEmpty()) return "";

        Set<String> colors = new java.util.HashSet<>();
        for (VariantInfo v : variants) {
            if (v.available && v.stock > 0 && v.color != null) {
                colors.add(v.color);
            }
        }
        return String.join(", ", colors);
    }

    /**
     * Lấy danh sách size có sẵn dưới dạng string
     */
    public String getSizes() {
        if (variants == null || variants.isEmpty()) return "";

        Set<String> sizes = new java.util.HashSet<>();
        for (VariantInfo v : variants) {
            if (v.available && v.stock > 0 && v.size != null) {
                sizes.add(v.size);
            }
        }
        return String.join(", ", sizes);
    }

    /**
     * Lấy tên category đầu tiên
     */
    public String getCategoryName() {
        if (categories == null || categories.isEmpty()) return null;
        return categories.get(0);
    }

    /**
     * Kiểm tra xem giá có trong khoảng không
     */
    public boolean isPriceInRange(Long min, Long max) {
        if (minPrice == null) return false;
        long price = minPrice.longValue();
        return price >= min && price <= max;
    }
}

