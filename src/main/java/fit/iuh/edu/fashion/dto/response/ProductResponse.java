package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String material;
    private String origin;
    private Boolean isActive;
    private BrandResponse brand;
    private List<CategoryResponse> categories;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;
    private Double averageRating;
    private Long totalReviews;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
