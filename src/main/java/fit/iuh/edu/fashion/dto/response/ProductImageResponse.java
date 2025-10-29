package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {
    private Long id;
    private String url;
    private String altText;
    private Integer sortOrder;
    private Long variantId; // ID của biến thể nếu ảnh thuộc về biến thể cụ thể
}
