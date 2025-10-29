package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.ProductCatalogDTO;
import fit.iuh.edu.fashion.services.CatalogCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller để debug AI - Kiểm tra dữ liệu trước khi gửi cho AI
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final CatalogCacheService catalogCacheService;

    /**
     * Test xem tìm được bao nhiêu sản phẩm
     */
    @GetMapping("/search-products")
    public ResponseEntity<Map<String, Object>> debugSearchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {

        log.info("DEBUG: Searching for keyword: {}", keyword);

        List<ProductCatalogDTO> products = catalogCacheService.searchProducts(keyword, limit);

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("limit", limit);
        result.put("productsFound", products.size());
        result.put("products", products);

        // Log chi tiết từng sản phẩm
        log.info("Found {} products:", products.size());
        products.forEach(p -> {
            log.info("  - {} (ID: {}) - Price: {} - Brand: {}",
                p.getName(), p.getId(), p.getMinPrice(), p.getBrandName());
            log.info("    Description: {}", p.toAiDescription());
        });

        return ResponseEntity.ok(result);
    }

    /**
     * Test message sẽ gửi cho AI
     */
    @GetMapping("/ai-message")
    public ResponseEntity<Map<String, Object>> debugAiMessage(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {

        List<ProductCatalogDTO> products = catalogCacheService.searchProducts(keyword, limit);

        // Build message giống hệt như AiAssistantService
        StringBuilder message = new StringBuilder();
        message.append("=== NHIỆM VỤ: GỢI Ý SẢN PHẨM ===\n\n");
        message.append("Khách hàng tìm kiếm: \"").append(keyword).append("\"\n\n");
        message.append("=== DANH SÁCH ").append(products.size()).append(" SẢN PHẨM CÓ SẴN ===\n");
        message.append("(CHỈ được giới thiệu các sản phẩm dưới đây, KHÔNG được tự tạo)\n\n");

        int index = 1;
        for (ProductCatalogDTO p : products) {
            message.append("SẢN PHẨM ").append(index++).append(":\n");
            message.append("- Tên: ").append(p.getName()).append("\n");

            if (p.getBrandName() != null) {
                message.append("- Thương hiệu: ").append(p.getBrandName()).append("\n");
            }

            if (p.getMinPrice() != null) {
                message.append("- Giá: ").append(String.format("%,d₫", p.getMinPrice().longValue())).append("\n");
            }

            if (p.getVariants() != null && !p.getVariants().isEmpty()) {
                java.util.Set<String> colors = new java.util.HashSet<>();
                java.util.Set<String> sizes = new java.util.HashSet<>();

                for (ProductCatalogDTO.VariantInfo v : p.getVariants()) {
                    if (v.isAvailable() && v.getStock() > 0) {
                        if (v.getColor() != null) colors.add(v.getColor());
                        if (v.getSize() != null) sizes.add(v.getSize());
                    }
                }

                if (!colors.isEmpty()) {
                    message.append("- Màu sắc: ").append(String.join(", ", colors)).append("\n");
                }
                if (!sizes.isEmpty()) {
                    message.append("- Kích thước: ").append(String.join(", ", sizes)).append("\n");
                }
            }

            message.append("\n");
        }

        message.append("=== YÊU CẦU TRẢ LỜI ===\n");
        message.append("1. Giới thiệu CHÍNH XÁC ").append(products.size()).append(" sản phẩm trên\n");
        message.append("2. Sử dụng ĐÚNG tên sản phẩm từ danh sách\n");
        message.append("3. Ghi rõ giá tiền, màu sắc, kích thước\n");
        message.append("4. TUYỆT ĐỐI KHÔNG tự tạo sản phẩm khác\n");

        Map<String, Object> result = new HashMap<>();
        result.put("keyword", keyword);
        result.put("productsFound", products.size());
        result.put("messageToAI", message.toString());
        result.put("messageLength", message.length());

        return ResponseEntity.ok(result);
    }
}

