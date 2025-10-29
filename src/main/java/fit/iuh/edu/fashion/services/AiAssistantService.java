package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.AiChatRequest;
import fit.iuh.edu.fashion.dto.AiChatResponse;
import fit.iuh.edu.fashion.dto.CatalogDataDTO;
import fit.iuh.edu.fashion.dto.ProductCatalogDTO;
import fit.iuh.edu.fashion.dto.UserIntentDTO;
import fit.iuh.edu.fashion.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAssistantService {

    private final ChatClient chatClient;
    private final ProductRepository productRepository;
    private final CatalogCacheService catalogCacheService;
    private final RestClient.Builder restClientBuilder;
    private final UserIntentAnalyzer intentAnalyzer;

    @Value("${spring.ai.openai.base-url}")
    private String lmStudioBaseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String requiredModel;

    @Value("${app.chat.default-system:Bạn là trợ lý mua sắm thời trang. Trả lời ngắn gọn, thân thiện trong 2-3 câu.}")
    private String defaultSystem;

    private RestClient restClient;
    private volatile boolean lmStudioChecked = false;
    private volatile boolean lmStudioAvailable = false;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("AI Assistant Service initialized with ChatClient");
        log.info("LM Studio URL: {}", lmStudioBaseUrl);
        log.info("Required Model: {}", requiredModel);

        // Cấu hình RestClient với timeout rất ngắn (1 giây) cho việc kiểm tra LM Studio
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Generate response từ AI - CACHED để tránh gọi lại với câu hỏi giống nhau
     */
    @Cacheable(value = "aiResponses", key = "#message + '_' + #systemPrompt")
    public String generate(String message, String systemPrompt) {
        log.info("Generating AI response for: {}", message);

        // Kiểm tra LM Studio (chỉ check 1 lần)
        if (!isLmStudioAvailable()) {
            throw new RuntimeException("LM Studio is not available");
        }

        try {
            String system = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt
                : defaultSystem;

            // Gọi AI với ChatClient - KHÔNG thêm product context tự động
            // để giảm độ phức tạp và thời gian xử lý
            String response = chatClient.prompt()
                    .system(system)
                    .user(message)
                    .call()
                    .content();

            log.info("AI response generated successfully");
            return response;

        } catch (Exception e) {
            log.error("Error generating AI response: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Kiểm tra LM Studio có sẵn sàng không - CHỈ CHECK 1 LẦN khi khởi động
     */
    private boolean isLmStudioAvailable() {
        // Nếu đã check rồi thì trả về kết quả cache
        if (lmStudioChecked) {
            return lmStudioAvailable;
        }

        try {
            log.debug("Checking LM Studio availability...");

            String response = restClient.get()
                    .uri(lmStudioBaseUrl + "/v1/models")
                    .retrieve()
                    .body(String.class);

            lmStudioAvailable = response != null && !response.isEmpty();
            lmStudioChecked = true;

            if (lmStudioAvailable) {
                log.info("✓ LM Studio is AVAILABLE and ready");
            } else {
                log.warn("✗ LM Studio returned empty response");
            }

            return lmStudioAvailable;

        } catch (Exception e) {
            log.warn("✗ LM Studio is NOT available: {}", e.getMessage());
            lmStudioAvailable = false;
            lmStudioChecked = true;
            return false;
        }
    }

    /**
     * Chat đơn giản với AI - wrapper cho API endpoint
     * NÂNG CẤP: Tự động phát hiện intent tìm kiếm sản phẩm với phân tích thông minh
     */
    @Cacheable(value = "aiResponses", key = "'chat_' + #userMessage")
    public AiChatResponse chat(String userMessage) {
        log.info("Processing simple AI chat: {}", userMessage);

        try {
            // BƯỚC 1: Phân tích ý định người dùng
            UserIntentDTO intent = intentAnalyzer.analyzeIntent(userMessage);
            log.info("✓ Intent analyzed: type={}, productType={}, category={}, brand={}",
                     intent.getIntentType(), intent.getProductType(),
                     intent.getCategory(), intent.getBrand());

            // BƯỚC 2: Xử lý theo loại ý định
            switch (intent.getIntentType()) {
                case PRODUCT_SEARCH:
                case PRODUCT_RECOMMENDATION:
                    // Tìm kiếm sản phẩm thông minh với các tiêu chí đã phân tích
                    return searchProductsByIntent(intent);

                case PRODUCT_COMPARE:
                    // So sánh sản phẩm
                    return compareProductsByIntent(intent);

                case INFORMATION_QUERY:
                case GENERAL_CHAT:
                default:
                    // Chat thông thường với context catalog
                    return chatWithCatalogContext(userMessage);
            }
        } catch (Exception e) {
            log.error("Error processing AI chat: ", e);
            return handleError(e);
        }
    }

    /**
     * Tìm kiếm sản phẩm dựa trên ý định đã phân tích
     */
    private AiChatResponse searchProductsByIntent(UserIntentDTO intent) {
        log.info("Searching products by intent: {}", intent);

        try {
            // Tìm sản phẩm với các tiêu chí đã phân tích
            List<ProductCatalogDTO> products = searchProductsAdvanced(intent);

            if (products.isEmpty()) {
                log.warn("No products found for intent: {}", intent);
                return suggestAlternatives(intent.toQueryString());
            }

            log.info("Found {} products matching intent", products.size());

            // Lấy catalog context
            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String systemPrompt = catalogData.toSystemPrompt();

            // Tạo câu hỏi tư vấn với thông tin chi tiết về ý định
            String message = buildIntelligentMessage(intent, products);

            log.debug("Message sent to AI:\n{}", message);

            String response = generate(message, systemPrompt);
            log.info("AI response generated successfully");

            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in search by intent: ", e);
            return handleError(e);
        }
    }

    /**
     * Cung cấp hướng dẫn chọn size chi tiết
     */
    private AiChatResponse provideSizeGuide(UserIntentDTO intent) {
        log.info("Providing size guide for: {}", intent.getOriginalMessage());

        String productType = intent.getProductType();
        if (productType == null) {
            productType = extractProductTypeFromMessage(intent.getOriginalMessage());
        }

        StringBuilder guide = new StringBuilder();
        guide.append("📏 **HƯỚNG DẪN CHỌN SIZE**\n\n");

        // Xác định loại sản phẩm và đưa ra bảng size tương ứng
        if (productType != null && (productType.contains("áo") || productType.contains("ao"))) {
            guide.append(getSizeGuideForShirts());
        } else if (productType != null && (productType.contains("quần") || productType.contains("quan"))) {
            guide.append(getSizeGuideForPants());
        } else if (productType != null && (productType.contains("váy") || productType.contains("vay") || productType.contains("đầm") || productType.contains("dam"))) {
            guide.append(getSizeGuideForDresses());
        } else if (productType != null && (productType.contains("giày") || productType.contains("giay"))) {
            guide.append(getSizeGuideForShoes());
        } else {
            // Hướng dẫn chung cho tất cả loại sản phẩm
            guide.append(getGeneralSizeGuide());
        }

        guide.append("\n\n💡 **LỜI KHUYÊN:**\n");
        guide.append("- Nếu bạn ở giữa 2 size, hãy chọn size lớn hơn để thoải mái\n");
        guide.append("- Đo vào buổi chiều/tối vì cơ thể hơi phồng lên trong ngày\n");
        guide.append("- Với áo len/áo khoác, có thể chọn size lớn hơn 1 size để mặc thoải mái\n");
        guide.append("- Liên hệ shop để được tư vấn size phù hợp nhất!\n\n");
        guide.append("📞 Cần hỗ trợ thêm? Hãy inbox shop hoặc gọi hotline nhé!");

        return new AiChatResponse(guide.toString(), requiredModel, System.currentTimeMillis());
    }

    /**
     * Trích xuất loại sản phẩm từ message
     */
    private String extractProductTypeFromMessage(String message) {
        String lower = message.toLowerCase();
        if (containsAny(lower, "áo", "ao")) return "áo";
        if (containsAny(lower, "quần", "quan")) return "quần";
        if (containsAny(lower, "váy", "vay", "đầm", "dam")) return "váy";
        if (containsAny(lower, "giày", "giay")) return "giày";
        return null;
    }

    /**
     * Bảng size cho áo
     */
    private String getSizeGuideForShirts() {
        return """
        **BẢNG SIZE ÁO NAM/NỮ:**
        
        | SIZE | CHIỀU CAO (cm) | CÂN NẶNG (kg) | RỘNG VAI (cm) | VÒNG NGỰC (cm) | DÀI ÁO (cm) |
        |------|----------------|---------------|---------------|----------------|-------------|
        | S    | 155-160        | 45-52         | 38-40         | 82-86          | 60-62       |
        | M    | 160-165        | 52-58         | 40-42         | 86-90          | 62-64       |
        | L    | 165-170        | 58-65         | 42-44         | 90-94          | 64-66       |
        | XL   | 170-175        | 65-72         | 44-46         | 94-98          | 66-68       |
        | XXL  | 175-180        | 72-80         | 46-48         | 98-104         | 68-70       |
        
        **CÁCH ĐO:**
        1. **Vòng ngực**: Đo vòng quanh phần rộng nhất của ngực
        2. **Rộng vai**: Đo từ điểm cao nhất vai này sang vai kia
        3. **Dài áo**: Đo từ vai xuống đến eo/mông tùy kiểu áo
        """;
    }

    /**
     * Bảng size cho quần
     */
    private String getSizeGuideForPants() {
        return """
        **BẢNG SIZE QUẦN NAM/NỮ:**
        
        | SIZE | VÒNG EO (cm) | VÒNG MÔNG (cm) | DÀI QUẦN (cm) | SIZE QUỐC TẾ |
        |------|--------------|----------------|---------------|--------------|
        | 26   | 64-67        | 86-89          | 95-97         | XS           |
        | 27   | 67-70        | 89-92          | 96-98         | S            |
        | 28   | 70-73        | 92-95          | 97-99         | S-M          |
        | 29   | 73-76        | 95-98          | 98-100        | M            |
        | 30   | 76-79        | 98-101         | 99-101        | M-L          |
        | 31   | 79-82        | 101-104        | 100-102       | L            |
        | 32   | 82-85        | 104-107        | 101-103       | L-XL         |
        | 33   | 85-88        | 107-110        | 102-104       | XL           |
        | 34   | 88-91        | 110-113        | 103-105       | XXL          |
        
        **CÁCH ĐO:**
        1. **Vòng eo**: Đo vòng quanh phần nhỏ nhất của eo
        2. **Vòng mông**: Đo vòng quanh phần rộng nhất của mông
        3. **Dài quần**: Đo từ eo xuống mắt cá chân
        """;
    }

    /**
     * Bảng size cho váy/đầm
     */
    private String getSizeGuideForDresses() {
        return """
        **BẢNG SIZE VÁY/ĐẦM:**
        
        | SIZE | VÒNG NGỰC (cm) | VÒNG EO (cm) | VÒNG MÔNG (cm) | DÀI VÁY (cm) |
        |------|----------------|--------------|----------------|--------------|
        | S    | 80-84          | 62-66        | 86-90          | 85-90        |
        | M    | 84-88          | 66-70        | 90-94          | 88-93        |
        | L    | 88-92          | 70-74        | 94-98          | 90-95        |
        | XL   | 92-96          | 74-78        | 98-102         | 92-97        |
        | XXL  | 96-100         | 78-82        | 102-106        | 94-99        |
        
        **CÁCH ĐO:**
        1. **Vòng ngực**: Đo vòng quanh phần đầy nhất của ngực
        2. **Vòng eo**: Đo vòng quanh phần nhỏ nhất của eo
        3. **Vòng mông**: Đo vòng quanh phần rộng nhất của mông
        4. **Dài váy**: Đo từ vai xuống hem váy
        """;
    }

    /**
     * Bảng size cho giày
     */
    private String getSizeGuideForShoes() {
        return """
        **BẢNG SIZE GIÀY:**
        
        | SIZE VN | SIZE US (Nam) | SIZE US (Nữ) | SIZE EU | CHIỀU DÀI CHÂN (cm) |
        |---------|---------------|--------------|---------|---------------------|
        | 36      | 4             | 5.5          | 36      | 22.5                |
        | 37      | 4.5           | 6            | 37      | 23.0                |
        | 38      | 5             | 6.5          | 38      | 23.5                |
        | 39      | 6             | 7.5          | 39      | 24.0                |
        | 40      | 6.5           | 8            | 40      | 24.5                |
        | 41      | 7.5           | 9            | 41      | 25.0                |
        | 42      | 8             | 9.5          | 42      | 25.5                |
        | 43      | 9             | 10.5         | 43      | 26.0                |
        | 44      | 9.5           | 11           | 44      | 26.5                |
        | 45      | 10.5          | 12           | 45      | 27.0                |
        
        **CÁCH ĐO:**
        1. Đứng thẳng, đặt bàn chân lên giấy
        2. Đánh dấu điểm dài nhất (từ gót đến ngón chân dài nhất)
        3. Dùng thước đo khoảng cách giữa 2 điểm
        4. Cộng thêm 0.5-1cm để chọn size phù hợp
        """;
    }

    /**
     * Hướng dẫn size chung
     */
    private String getGeneralSizeGuide() {
        return """
        **HƯỚNG DẪN CHỌN SIZE CHUNG:**
        
        **1. ÁO (Áo thun, Áo sơ mi, Áo khoác):**
        - S: 45-52kg, cao 155-160cm
        - M: 52-58kg, cao 160-165cm
        - L: 58-65kg, cao 165-170cm
        - XL: 65-72kg, cao 170-175cm
        - XXL: 72-80kg, cao 175-180cm
        
        **2. QUẦN (Jean, Kaki, Short):**
        - 27-28: Vòng eo 67-73cm
        - 29-30: Vòng eo 73-79cm
        - 31-32: Vòng eo 79-85cm
        - 33-34: Vòng eo 85-91cm
        
        **3. VÁY/ĐẦM:**
        - S: Vòng ngực 80-84cm, Vòng eo 62-66cm
        - M: Vòng ngực 84-88cm, Vòng eo 66-70cm
        - L: Vòng ngực 88-92cm, Vòng eo 70-74cm
        - XL: Vòng ngực 92-96cm, Vòng eo 74-78cm
        
        **4. GIÀY DÉP:**
        - 36-37: Dài chân 22.5-23.5cm
        - 38-39: Dài chân 23.5-24.5cm
        - 40-41: Dài chân 24.5-25.5cm
        - 42-43: Dài chân 25.5-26.5cm
        """;
    }

    /**
     * Helper method để check keywords
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * So sánh sản phẩm dựa trên ý định
     */
    private AiChatResponse compareProductsByIntent(UserIntentDTO intent) {
        log.info("Comparing products by intent: {}", intent);

        try {
            List<ProductCatalogDTO> products = searchProductsAdvanced(intent);

            if (products.size() < 2) {
                return new AiChatResponse(
                    "Xin lỗi, tôi cần ít nhất 2 sản phẩm để so sánh. " +
                    "Hiện tại chỉ tìm thấy " + products.size() + " sản phẩm.",
                    requiredModel,
                    System.currentTimeMillis()
                );
            }

            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String systemPrompt = catalogData.toSystemPrompt();

            String message = String.format(
                "Khách hàng muốn so sánh các sản phẩm: %s\n\n" +
                "Các sản phẩm cần so sánh:\n%s\n\n" +
                "YÊU CẦU: Hãy so sánh chi tiết về:\n" +
                "- Giá cả\n" +
                "- Chất liệu và chất lượng\n" +
                "- Màu sắc và size có sẵn\n" +
                "- Ưu điểm và nhược điểm của từng sản phẩm\n" +
                "- Gợi ý sản phẩm phù hợp nhất dựa trên nhu cầu",
                intent.getOriginalMessage(),
                products.stream()
                    .limit(5)
                    .map(ProductCatalogDTO::toAiDescription)
                    .collect(Collectors.joining("\n\n", "", ""))
            );

            String response = generate(message, systemPrompt);
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in compare by intent: ", e);
            return handleError(e);
        }
    }

    /**
     * Chat thông thường với context catalog
     */
    private AiChatResponse chatWithCatalogContext(String userMessage) {
        try {
            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();

            // Phát hiện loại câu hỏi để tạo system prompt phù hợp
            String systemPrompt;
            String lowerMessage = userMessage.toLowerCase();

            if (containsAny(lowerMessage, "phối đồ", "mix đồ", "kết hợp", "cách mặc", "outfit", "phong cách")) {
                // Câu hỏi về phối đồ/tư vấn thời trang
                systemPrompt = """
                    Bạn là chuyên gia tư vấn thời trang chuyên nghiệp.
                    
                    NHIỆM VỤ:
                    - Tư vấn cách phối đồ phù hợp với hoàn cảnh (đi làm, đi chơi, dự tiệc...)
                    - Gợi ý các loại trang phục, màu sắc, phụ kiện phù hợp
                    - Đưa ra lời khuyên thực tế, dễ áp dụng
                    - Giải thích TẠI SAO phối đồ đó phù hợp
                    
                    FORMAT TRẢ LỜI:
                    1. Phân tích hoàn cảnh/mục đích
                    2. Gợi ý các item cần có (áo, quần, giày, phụ kiện)
                    3. Lời khuyên về màu sắc và style
                    4. Tips thêm (nếu có)
                    
                    LƯU Ý:
                    - Trả lời cụ thể, rõ ràng
                    - Ngắn gọn 5-7 câu
                    - Thân thiện, dễ hiểu
                    - Không cần giới thiệu sản phẩm cụ thể trừ khi được hỏi
                    """;
            } else {
                // Câu hỏi chung về thời trang/cửa hàng
                systemPrompt = catalogData.toSystemPrompt() +
                    "\n\nBạn đang trả lời câu hỏi chung về cửa hàng hoặc thời trang. Trả lời ngắn gọn, thân thiện và hữu ích.";
            }

            String response = generate(userMessage, systemPrompt);
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Error in chat with catalog context: ", e);
            return handleError(e);
        }
    }

    /**
     * Chat với ngữ cảnh bổ sung
     */
    public AiChatResponse chatWithContext(AiChatRequest request) {
        log.info("Processing AI chat with context: {}", request.getMessage());

        try {
            String response = generate(request.getMessage(), request.getContext());
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Error processing AI chat with context: ", e);
            return new AiChatResponse(
                "Xin lỗi, tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau.",
                "error",
                System.currentTimeMillis()
            );
        }
    }

    /**
     * Chat với AI về sản phẩm - có context về catalog
     */
    @Cacheable(value = "aiResponses", key = "'product_chat_' + #userMessage")
    public AiChatResponse chatWithProductContext(String userMessage) {
        log.info("Processing product consultation: {}", userMessage);

        try {
            // Lấy catalog data (cached)
            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();

            // Tạo system prompt từ catalog
            String systemPrompt = catalogData.toSystemPrompt();

            // Phân tích câu hỏi và lấy sản phẩm liên quan
            String enhancedMessage = buildEnhancedMessage(userMessage, catalogData);

            // Gọi AI với context đầy đủ
            String response = generate(enhancedMessage, systemPrompt);

            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in product consultation: ", e);
            return handleError(e);
        }
    }

    /**
     * Tìm kiếm và tư vấn sản phẩm theo từ khóa - VERSION CẢI TIẾN
     */
    @Cacheable(value = "aiResponses", key = "'search_' + #keyword + '_' + #limit")
    public AiChatResponse searchAndAdvise(String keyword, int limit) {
        log.info("Searching and advising for keyword: {}", keyword);

        try {
            // Tìm sản phẩm phù hợp (cached)
            List<ProductCatalogDTO> products = catalogCacheService.searchProducts(keyword, limit);

            // LOG để debug
            log.info("Found {} products for keyword '{}'", products.size(), keyword);
            if (!products.isEmpty()) {
                products.forEach(p -> log.info("- Product: {} - {}", p.getName(), p.getMinPrice()));
            }

            if (products.isEmpty()) {
                log.warn("No products found for keyword: {}", keyword);
                return suggestAlternatives(keyword);
            }

            // Lấy catalog context
            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String systemPrompt = catalogData.toSystemPrompt();

            // Tạo câu hỏi tư vấn với format cải tiến
            String message = buildProductListMessage(keyword, products);

            // LOG message gửi cho AI
            log.debug("Message sent to AI:\n{}", message);

            String response = generate(message, systemPrompt);

            log.info("AI response generated successfully");

            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in search and advise: ", e);
            return handleError(e);
        }
    }

    /**
     * Tư vấn theo thương hiệu
     */
    @Cacheable(value = "aiResponses", key = "'brand_' + #brandId + '_' + #question")
    public AiChatResponse consultByBrand(Long brandId, String question, int limit) {
        log.info("Consulting for brand ID: {}, question: {}", brandId, question);

        try {
            List<ProductCatalogDTO> products = catalogCacheService.getProductsByBrand(brandId, limit);

            if (products.isEmpty()) {
                return new AiChatResponse(
                    "Xin lỗi, hiện tại thương hiệu này chưa có sản phẩm nào.",
                    requiredModel,
                    System.currentTimeMillis()
                );
            }

            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String brandName = catalogData.getBrands().stream()
                .filter(b -> b.getId().equals(brandId))
                .findFirst()
                .map(CatalogDataDTO.BrandInfo::getName)
                .orElse("Unknown");

            String systemPrompt = catalogData.toSystemPrompt();
            String message = String.format(
                "Khách hàng hỏi về thương hiệu %s: %s\n\nCác sản phẩm có sẵn:\n%s",
                brandName,
                question,
                products.stream()
                    .map(ProductCatalogDTO::toAiDescription)
                    .collect(Collectors.joining("\n- ", "- ", ""))
            );

            String response = generate(message, systemPrompt);
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in brand consultation: ", e);
            return handleError(e);
        }
    }

    /**
     * Tư vấn theo danh mục
     */
    @Cacheable(value = "aiResponses", key = "'category_' + #categoryId + '_' + #question")
    public AiChatResponse consultByCategory(Long categoryId, String question, int limit) {
        log.info("Consulting for category ID: {}, question: {}", categoryId, question);

        try {
            List<ProductCatalogDTO> products = catalogCacheService.getProductsByCategory(categoryId, limit);

            if (products.isEmpty()) {
                return new AiChatResponse(
                    "Xin lỗi, danh mục này hiện chưa có sản phẩm nào.",
                    requiredModel,
                    System.currentTimeMillis()
                );
            }

            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String categoryName = catalogData.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .map(CatalogDataDTO.CategoryInfo::getName)
                .orElse("Unknown");

            String systemPrompt = catalogData.toSystemPrompt();
            String message = String.format(
                "Khách hàng hỏi về danh mục %s: %s\n\nCác sản phẩm có sẵn:\n%s",
                categoryName,
                question,
                products.stream()
                    .map(ProductCatalogDTO::toAiDescription)
                    .collect(Collectors.joining("\n- ", "- ", ""))
            );

            String response = generate(message, systemPrompt);
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Error in category consultation: ", e);
            return handleError(e);
        }
    }

    /**
     * Build enhanced message với product context
     */
    private String buildEnhancedMessage(String userMessage, CatalogDataDTO catalogData) {
        // Phân tích từ khóa trong câu hỏi
        String lowerMessage = userMessage.toLowerCase();

        StringBuilder enhanced = new StringBuilder();
        enhanced.append("Câu hỏi của khách: ").append(userMessage).append("\n\n");

        // Tìm sản phẩm liên quan nếu có từ khóa
        if (containsProductKeyword(lowerMessage)) {
            List<ProductCatalogDTO> products = findRelevantProducts(userMessage, 5);
            if (!products.isEmpty()) {
                enhanced.append("Sản phẩm liên quan:\n");
                products.forEach(p -> enhanced.append("- ").append(p.toAiDescription()).append("\n"));
                enhanced.append("\n");
            }
        }

        return enhanced.toString();
    }

    /**
     * Kiểm tra xem câu hỏi có chứa từ khóa sản phẩm không
     */
    private boolean containsProductKeyword(String message) {
        String[] keywords = {"áo", "quần", "váy", "giày", "túi", "phụ kiện",
            "sản phẩm", "mua", "tìm", "có", "màu", "size", "giá"};

        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tìm sản phẩm liên quan từ câu hỏi
     */
    private List<ProductCatalogDTO> findRelevantProducts(String query, int limit) {
        // Extract keywords
        String[] words = query.toLowerCase().split("\\s+");

        for (String word : words) {
            if (word.length() > 2) {
                List<ProductCatalogDTO> products = catalogCacheService.searchProducts(word, limit);
                if (!products.isEmpty()) {
                    return products;
                }
            }
        }

        // Fallback: return top products
        return catalogCacheService.getTopProducts(limit);
    }

    /**
     * Build message từ danh sách sản phẩm
     */
    private String buildProductListMessage(String keyword, List<ProductCatalogDTO> products) {
        StringBuilder message = new StringBuilder();
        message.append("Khách hàng tìm kiếm: \"").append(keyword).append("\"\n\n");
        message.append("Tìm thấy ").append(products.size()).append(" sản phẩm có sẵn trong cửa hàng:\n\n");

        int index = 1;
        for (ProductCatalogDTO p : products) {
            message.append(index++).append(". ").append(p.toAiDescription()).append("\n");
        }

        message.append("\n");
        message.append("YÊU CẦU:\n");
        message.append("- Nếu khách hỏi về MỘT sản phẩm cụ thể trong danh sách → Cung cấp thông tin chi tiết về sản phẩm đó\n");
        message.append("- Nếu khách tìm kiếm chung → Giới thiệu ").append(Math.min(5, products.size()))
               .append(" sản phẩm phù hợp nhất\n");
        message.append("- Cung cấp thông tin CỤ THỂ: tên, giá, màu sắc, size\n");
        message.append("- CHỈ sử dụng thông tin từ danh sách sản phẩm ở trên\n");
        message.append("\nLƯU Ý: Khi khách hỏi tên sản phẩm cụ thể, đó là yêu cầu XEM THÔNG TIN, không phải TẠO MỚI sản phẩm.\n");
        message.append("Format: Trả lời ngắn gọn, thân thiện, tập trung vào sản phẩm khách quan tâm.");

        return message.toString();
    }

    /**
     * Gợi ý sản phẩm thay thế khi không tìm thấy
     */
    private AiChatResponse suggestAlternatives(String keyword) {
        try {
            // Lấy top products thay thế
            List<ProductCatalogDTO> alternatives = catalogCacheService.getTopProducts(5);

            CatalogDataDTO catalogData = catalogCacheService.getCatalogData();
            String systemPrompt = catalogData.toSystemPrompt();

            String message = String.format(
                "Khách hàng tìm '%s' nhưng không có sản phẩm phù hợp.\n" +
                "Gợi ý các sản phẩm thay thế:\n%s\n" +
                "Hãy tư vấn thân thiện và gợi ý sản phẩm tương tự.",
                keyword,
                alternatives.stream()
                    .map(ProductCatalogDTO::toAiDescription)
                    .collect(Collectors.joining("\n- ", "- ", ""))
            );

            String response = generate(message, systemPrompt);
            return new AiChatResponse(response, requiredModel, System.currentTimeMillis());

        } catch (Exception e) {
            return new AiChatResponse(
                "Xin lỗi, tôi không tìm thấy sản phẩm phù hợp với '" + keyword + "'. " +
                "Bạn có thể mô tả chi tiết hơn hoặc thử từ khóa khác không?",
                requiredModel,
                System.currentTimeMillis()
            );
        }
    }

    /**
     * Xử lý lỗi chung
     */
    private AiChatResponse handleError(Exception e) {
        String errorMsg = e.getMessage() != null ? e.getMessage() : "";

        if (errorMsg.contains("LM Studio is not available") ||
            errorMsg.contains("Connection refused") ||
            errorMsg.contains("connect")) {
            return new AiChatResponse(
                "❌ Không thể kết nối tới LM Studio!\n\n" +
                "Hướng dẫn khắc phục:\n" +
                "1. Mở LM Studio\n" +
                "2. Vào tab 'Local Server'\n" +
                "3. Chọn model: " + requiredModel + "\n" +
                "4. Click 'START SERVER'\n" +
                "5. Đảm bảo port là 1234\n\n" +
                "Sau đó thử lại!",
                "error",
                System.currentTimeMillis()
            );
        }

        return new AiChatResponse(
            "Xin lỗi, tôi đang gặp sự cố kỹ thuật: " + errorMsg,
            "error",
            System.currentTimeMillis()
        );
    }

    /**
     * Tìm kiếm sản phẩm nâng cao dựa trên ý định đã phân tích
     */
    private List<ProductCatalogDTO> searchProductsAdvanced(UserIntentDTO intent) {
        log.info("Advanced search with intent: {}", intent);

        // Xây dựng query string từ intent
        String keyword = intent.toQueryString();

        if (keyword == null || keyword.isEmpty()) {
            keyword = intent.getOriginalMessage();
        }

        log.info("Search keyword: '{}'", keyword);

        // Tìm kiếm cơ bản trước
        List<ProductCatalogDTO> products;

        // Nếu keyword vẫn rỗng hoặc quá chung chung, lấy top products
        if (keyword == null || keyword.trim().isEmpty() || keyword.length() < 3) {
            log.info("Keyword is empty or too short, getting top products");
            products = catalogCacheService.getTopProducts(20);
        } else {
            products = catalogCacheService.searchProducts(keyword, 20);
        }

        // Lọc theo các tiêu chí bổ sung
        products = filterByIntent(products, intent);

        // Sắp xếp theo độ liên quan
        products = sortByRelevance(products, intent);

        // Giới hạn số lượng kết quả
        int limit = extractLimit(intent.getOriginalMessage());
        if (products.size() > limit) {
            products = products.subList(0, limit);
        }

        log.info("After filtering and sorting: {} products", products.size());

        return products;
    }

    /**
     * Lọc sản phẩm theo intent
     */
    private List<ProductCatalogDTO> filterByIntent(List<ProductCatalogDTO> products, UserIntentDTO intent) {
        return products.stream()
            .filter(p -> matchesPriceRange(p, intent.getPriceRange()))
            .filter(p -> matchesColors(p, intent.getColors()))
            .filter(p -> matchesSizes(p, intent.getSizes()))
            .collect(Collectors.toList());
    }

    /**
     * Kiểm tra sản phẩm có trong khoảng giá không
     */
    private boolean matchesPriceRange(ProductCatalogDTO product, UserIntentDTO.PriceRange priceRange) {
        if (priceRange == null) return true;
        if (product.getMinPrice() == null) return false;
        Long price = product.getMinPrice().longValue();
        return priceRange.isInRange(price);
    }

    /**
     * Kiểm tra sản phẩm có màu mong muốn không
     */
    private boolean matchesColors(ProductCatalogDTO product, List<String> colors) {
        if (colors == null || colors.isEmpty()) return true;
        if (product.getColors() == null || product.getColors().isEmpty()) return false;

        String productColors = product.getColors().toLowerCase();
        return colors.stream()
            .anyMatch(color -> productColors.contains(color.toLowerCase()));
    }

    /**
     * Kiểm tra sản phẩm có size mong muốn không
     */
    private boolean matchesSizes(ProductCatalogDTO product, List<String> sizes) {
        if (sizes == null || sizes.isEmpty()) return true;
        if (product.getSizes() == null || product.getSizes().isEmpty()) return false;

        String productSizes = product.getSizes().toLowerCase();
        return sizes.stream()
            .anyMatch(size -> productSizes.contains(size.toLowerCase()));
    }

    /**
     * Sắp xếp sản phẩm theo độ liên quan với intent
     */
    private List<ProductCatalogDTO> sortByRelevance(List<ProductCatalogDTO> products, UserIntentDTO intent) {
        // Tính điểm liên quan cho mỗi sản phẩm
        return products.stream()
            .sorted((p1, p2) -> {
                int score1 = calculateRelevanceScore(p1, intent);
                int score2 = calculateRelevanceScore(p2, intent);
                return Integer.compare(score2, score1); // Sắp xếp giảm dần
            })
            .collect(Collectors.toList());
    }

    /**
     * Tính điểm liên quan của sản phẩm với intent
     */
    private int calculateRelevanceScore(ProductCatalogDTO product, UserIntentDTO intent) {
        int score = 0;

        // Brand match
        if (intent.getBrand() != null && product.getBrandName() != null &&
            product.getBrandName().toLowerCase().contains(intent.getBrand().toLowerCase())) {
            score += 50;
        }

        // Category match
        if (intent.getCategory() != null && product.getCategoryName() != null &&
            product.getCategoryName().toLowerCase().contains(intent.getCategory().toLowerCase())) {
            score += 40;
        }

        // Product type match
        if (intent.getProductType() != null && product.getName() != null &&
            product.getName().toLowerCase().contains(intent.getProductType().toLowerCase())) {
            score += 30;
        }

        // Color match
        if (matchesColors(product, intent.getColors())) {
            score += 20;
        }

        // Size match
        if (matchesSizes(product, intent.getSizes())) {
            score += 20;
        }

        // Price match
        if (matchesPriceRange(product, intent.getPriceRange())) {
            score += 10;
        }

        return score;
    }

    /**
     * Xây dựng message thông minh dựa trên intent
     */
    private String buildIntelligentMessage(UserIntentDTO intent, List<ProductCatalogDTO> products) {
        StringBuilder message = new StringBuilder();

        // Header với câu hỏi gốc
        message.append("Câu hỏi của khách hàng: \"").append(intent.getOriginalMessage()).append("\"\n\n");

        // Thông tin về tiêu chí tìm kiếm
        message.append("Tiêu chí tìm kiếm đã phân tích:\n");

        if (intent.getProductType() != null) {
            message.append("- Loại sản phẩm: ").append(intent.getProductType()).append("\n");
        }
        if (intent.getCategory() != null) {
            message.append("- Danh mục: ").append(intent.getCategory()).append("\n");
        }
        if (intent.getBrand() != null) {
            message.append("- Thương hiệu: ").append(intent.getBrand()).append("\n");
        }
        if (intent.getColors() != null && !intent.getColors().isEmpty()) {
            message.append("- Màu sắc: ").append(String.join(", ", intent.getColors())).append("\n");
        }
        if (intent.getSizes() != null && !intent.getSizes().isEmpty()) {
            message.append("- Kích thước: ").append(String.join(", ", intent.getSizes())).append("\n");
        }
        if (intent.getPriceRange() != null) {
            message.append("- Khoảng giá: ").append(formatPrice(intent.getPriceRange().getMin()))
                   .append(" - ").append(formatPrice(intent.getPriceRange().getMax())).append("\n");
        }
        if (intent.getGender() != null) {
            message.append("- Giới tính: ").append(intent.getGender()).append("\n");
        }
        if (intent.getStyle() != null) {
            message.append("- Phong cách: ").append(intent.getStyle()).append("\n");
        }

        message.append("\n");

        // Danh sách sản phẩm phù hợp
        message.append("Tìm thấy ").append(products.size()).append(" sản phẩm phù hợp:\n\n");

        int index = 1;
        for (ProductCatalogDTO p : products) {
            message.append(index++).append(". ").append(p.toAiDescription()).append("\n\n");
        }

        // Yêu cầu cho AI
        message.append("YÊU CẦU:\n");
        message.append("1. Nếu khách hỏi về SẢN PHẨM CỤ THỂ trong danh sách → Cung cấp thông tin chi tiết về sản phẩm đó\n");
        message.append("2. Nếu khách tìm kiếm chung → Phân tích và giới thiệu các sản phẩm PHÙ HỢP NHẤT\n");
        message.append("3. Giải thích TẠI SAO sản phẩm đó phù hợp (màu, giá, phong cách, v.v.)\n");
        message.append("4. Cung cấp thông tin CỤ THỂ về: tên, giá, màu sắc có sẵn, size có sẵn\n");
        message.append("5. Gợi ý TOP 3-5 sản phẩm TỐT NHẤT theo thứ tự ưu tiên\n");
        message.append("6. CHỈ giới thiệu các sản phẩm CÓ TRONG DANH SÁCH TRÊN\n");
        message.append("\nLƯU Ý: Khi khách hỏi về 1 sản phẩm cụ thể có trong danh sách, hãy trả lời thông tin về sản phẩm đó, KHÔNG nói là 'tạo ra sản phẩm'.\n");

        return message.toString();
    }

    /**
     * Format giá tiền
     */
    private String formatPrice(Long price) {
        if (price == null || price == Long.MAX_VALUE) return "không giới hạn";
        if (price >= 1000000) {
            return String.format("%.1f triệu", price / 1000000.0);
        }
        if (price >= 1000) {
            return String.format("%dk", price / 1000);
        }
        return price + "đ";
    }

    /**
     * PHÁT HIỆN INTENT: Kiểm tra xem câu hỏi có phải là tìm kiếm sản phẩm không
     */
    private boolean isProductSearchIntent(String message) {
        String lower = message.toLowerCase();

        // Các mẫu câu tìm kiếm sản phẩm
        String[] searchPatterns = {
            "tìm", "find", "search", "cho tôi", "cho mình", "gợi ý",
            "giới thiệu", "có", "bán", "show", "hiển thị", "xem"
        };

        String[] productKeywords = {
            "sản phẩm", "áo", "quần", "váy", "đầm", "giày", "dép",
            "túi", "ba lô", "phụ kiện", "mũ", "nón", "kính", "thắt lưng"
        };

        // Kiểm tra có chứa cả search pattern và product keyword
        boolean hasSearchPattern = false;
        boolean hasProductKeyword = false;

        for (String pattern : searchPatterns) {
            if (lower.contains(pattern)) {
                hasSearchPattern = true;
                break;
            }
        }

        for (String keyword : productKeywords) {
            if (lower.contains(keyword)) {
                hasProductKeyword = true;
                break;
            }
        }

        return hasSearchPattern && hasProductKeyword;
    }

    /**
     * TRÍCH XUẤT từ khóa tìm kiếm từ câu hỏi
     */
    private String extractKeyword(String message) {
        String lower = message.toLowerCase();

        // Danh sách từ khóa sản phẩm phổ biến (ưu tiên keyword dài hơn)
        String[] keywords = {
            "áo sơ mi", "áo thun", "áo len", "áo khoác", "áo polo", "áo",
            "quần jean", "quần tây", "quần short", "quần dài", "quần",
            "váy", "đầm", "giày", "dép", "sandal", "sneaker",
            "túi xách", "ba lô", "balo", "túi",
            "phụ kiện", "mũ", "nón", "kính", "thắt lưng"
        };

        // Tìm keyword phù hợp nhất
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return keyword;
            }
        }

        // Fallback: trích xuất từ sau động từ
        String[] words = message.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            String word = words[i].toLowerCase();
            if (word.matches("tìm|find|search|cho|gợi|giới|có|bán|show|hiển|xem")) {
                if (i + 1 < words.length) {
                    return words[i + 1].toLowerCase();
                }
            }
        }

        return "sản phẩm"; // Default fallback
    }

    /**
     * TRÍCH XUẤT giới hạn số lượng sản phẩm từ câu hỏi
     */
    private int extractLimit(String message) {
        // Tìm số trong câu hỏi
        String[] words = message.split("\\s+");

        for (String word : words) {
            try {
                int num = Integer.parseInt(word.replaceAll("[^0-9]", ""));
                if (num > 0 && num <= 50) {
                    return num;
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return 5; // Default: 5 sản phẩm
    }
}
