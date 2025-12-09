package fit.iuh.edu.fashion.dto;

import lombok.*;

import java.io.Serializable;
import java.util.List;

/**
 * DTO chứa toàn bộ catalog data cho AI - CACHED in Redis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogDataDTO implements Serializable {
    private List<BrandInfo> brands;
    private List<CategoryInfo> categories;
    private List<ColorInfo> colors;
    private List<SizeInfo> sizes;
    private long totalProducts;
    private long activeProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandInfo implements Serializable {
        private Long id;
        private String name;
        private String description;
        private long productCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo implements Serializable {
        private Long id;
        private String name;
        private String description;
        private Long parentId;
        private String parentName;
        private long productCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColorInfo implements Serializable {
        private Long id;
        private String name;
        private String hex;
        private long productCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SizeInfo implements Serializable {
        private Long id;
        private String name;
        private String note;
        private long productCount;
    }

    /**
     * Tạo system prompt cho AI - VERSION CẢI TIẾN ĐỂ ÉP AI TUÂN THỦ
     */
    public String toSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Bạn là trợ lý mua sắm thời trang chuyên nghiệp của cửa hàng Fashion.\n\n");

        prompt.append("⚠️ QUY TẮC BẮT BUỘC PHẢI TUÂN THỦ:\n");
        prompt.append("1. CHỈ được giới thiệu sản phẩm CÓ TRONG DANH SÁCH được cung cấp\n");
        prompt.append("2. Khi khách hỏi về 1 sản phẩm cụ thể trong danh sách → Đó là yêu cầu XEM THÔNG TIN, hãy cung cấp chi tiết về sản phẩm đó\n");
        prompt.append("3. KHÔNG được tự sáng tạo hoặc thêm sản phẩm không có trong danh sách\n");
        prompt.append("4. PHẢI sử dụng ĐÚNG TÊN SẢN PHẨM từ danh sách\n");
        prompt.append("5. PHẢI ghi rõ: Tên - Giá - Màu sắc - Kích thước\n");
        prompt.append("6. Trả lời bằng tiếng Việt, ngắn gọn, thân thiện\n\n");

        prompt.append("📊 THÔNG TIN CỬA HÀNG:\n");
        prompt.append("- Tổng số sản phẩm: ").append(totalProducts).append("\n");
        prompt.append("- Sản phẩm đang bán: ").append(activeProducts).append("\n\n");

        if (brands != null && !brands.isEmpty()) {
            prompt.append("🏷️ THƯƠNG HIỆU:\n");
            brands.stream()
                .filter(b -> b.productCount > 0)
                .limit(10)
                .forEach(b -> prompt.append("- ").append(b.name)
                    .append(" (").append(b.productCount).append(" SP)\n"));
            prompt.append("\n");
        }

        if (categories != null && !categories.isEmpty()) {
            prompt.append("📁 DANH MỤC:\n");
            categories.stream()
                .filter(c -> c.productCount > 0)
                .limit(15)
                .forEach(c -> prompt.append("- ").append(c.name)
                    .append(" (").append(c.productCount).append(" SP)\n"));
            prompt.append("\n");
        }

        if (colors != null && !colors.isEmpty()) {
            prompt.append("🎨 MÀU SẮC: ");
            prompt.append(colors.stream()
                .filter(c -> c.productCount > 0)
                .map(c -> c.name)
                .collect(java.util.stream.Collectors.joining(", ")));
            prompt.append("\n\n");
        }

        if (sizes != null && !sizes.isEmpty()) {
            prompt.append("📏 KÍCH THƯỚC: ");
            prompt.append(sizes.stream()
                .filter(s -> s.productCount > 0)
                .map(s -> s.name)
                .collect(java.util.stream.Collectors.joining(", ")));
            prompt.append("\n\n");
        }

        prompt.append("✅ FORMAT TRẢ LỜI:\n");
        prompt.append("- Nếu khách hỏi 1 sản phẩm cụ thể: \"[Tên sản phẩm] có giá [X]₫, màu [màu], size [size]. [Mô tả ngắn về sản phẩm]\"\n");
        prompt.append("- Nếu khách tìm kiếm chung: Liệt kê 3-5 sản phẩm phù hợp nhất với đầy đủ thông tin\n\n");

        prompt.append("❌ TUYỆT ĐỐI KHÔNG:\n");
        prompt.append("- Nói rằng khách 'tạo ra sản phẩm' khi họ chỉ đang hỏi thông tin\n");
        prompt.append("- Từ chối cung cấp thông tin về sản phẩm có trong danh sách\n");
        prompt.append("- Tự tạo tên sản phẩm hoặc thêm sản phẩm không có trong danh sách\n");
        prompt.append("- Bỏ qua thông tin giá, màu, size\n");

        return prompt.toString();
    }
}
