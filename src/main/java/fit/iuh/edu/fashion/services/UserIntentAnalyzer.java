package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.UserIntentDTO;
import fit.iuh.edu.fashion.models.Category;
import fit.iuh.edu.fashion.repositories.BrandRepository;
import fit.iuh.edu.fashion.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service phân tích ý định người dùng từ câu hỏi tự nhiên
 * Phát hiện: loại sản phẩm, danh mục, thương hiệu, giá, màu sắc, kích thước, v.v.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserIntentAnalyzer {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    /**
     * Phân tích ý định người dùng từ câu hỏi
     */
    public UserIntentDTO analyzeIntent(String userMessage) {
        log.info("Analyzing user intent for: {}", userMessage);

        String normalized = normalizeVietnamese(userMessage.toLowerCase());

        UserIntentDTO intent = UserIntentDTO.builder()
                .originalMessage(userMessage)
                .normalizedMessage(normalized)
                .build();

        // 1. Phát hiện loại intent
        intent.setIntentType(detectIntentType(normalized));

        // 2. Trích xuất thông tin sản phẩm
        intent.setProductType(extractProductType(normalized));
        intent.setCategory(extractCategory(normalized));
        intent.setBrand(extractBrand(normalized));

        // 3. Trích xuất thuộc tính
        intent.setColors(extractColors(normalized));
        intent.setSizes(extractSizes(normalized));
        intent.setPriceRange(extractPriceRange(normalized));
        intent.setGender(extractGender(normalized));
        intent.setStyle(extractStyle(normalized));

        // 4. Trích xuất từ khóa tìm kiếm
        intent.setSearchKeywords(extractSearchKeywords(normalized, intent));

        // 5. Xác định độ ưu tiên các tiêu chí
        intent.setPriority(determinePriority(normalized));

        log.info("Intent analysis result: {}", intent);

        return intent;
    }

    /**
     * Phát hiện loại ý định chính
     */
    private UserIntentDTO.IntentType detectIntentType(String message) {
        // Hỏi về size / tư vấn size (ưu tiên cao nhất)
        if (containsAny(message, "size", "co", "cỡ", "kich thuoc", "kích thước", "do size", "đo size",
                        "chon size", "chọn size", "huong dan size", "hướng dẫn size",
                        "bang size", "bảng size", "tu van size", "tư vấn size",
                        "size nao", "size nào", "mac size", "mặc size", "dung size", "đúng size")) {
            return UserIntentDTO.IntentType.SIZE_GUIDE;
        }

        // Hỏi về phối đồ, mix đồ, cách kết hợp (ưu tiên cao)
        if (containsAny(message, "phoi do", "phối đồ", "mix do", "mix đồ", "ket hop", "kết hợp",
                        "cach mac", "cách mặc", "mac the nao", "mặc thế nào", "mac gi", "mặc gì",
                        "di lam", "đi làm", "di choi", "đi chơi", "di tiec", "đi tiệc",
                        "outfit", "look", "style nao", "style nào", "phong cach", "phong cách")) {
            return UserIntentDTO.IntentType.INFORMATION_QUERY;
        }

        // Xem sản phẩm (mới nhất, bán chạy, trending, hot...)
        if (containsAny(message, "san pham", "sản phẩm", "product", "xem", "show", "hien thi", "hiển thị")) {
            return UserIntentDTO.IntentType.PRODUCT_SEARCH;
        }

        // Tìm kiếm sản phẩm
        if (containsAny(message, "tim", "tìm", "find", "search", "cho toi", "cho tôi", "cho minh", "cho mình", "muon", "muốn", "can", "cần", "co", "có")) {
            return UserIntentDTO.IntentType.PRODUCT_SEARCH;
        }

        // So sánh sản phẩm
        if (containsAny(message, "so sanh", "so sánh", "compare", "khac nhau", "khác nhau", "giong", "giống", "tuong tu", "tương tự")) {
            return UserIntentDTO.IntentType.PRODUCT_COMPARE;
        }

        // Tư vấn mua hàng (chỉ khi có từ khóa sản phẩm cụ thể)
        if (containsAny(message, "nen mua", "nên mua", "nen chon", "nên chọn", "goi y san pham", "gợi ý sản phẩm", "recommend product")) {
            return UserIntentDTO.IntentType.PRODUCT_RECOMMENDATION;
        }

        // Hỏi thông tin
        if (containsAny(message, "the nao", "thế nào", "how", "la gi", "là gì", "what", "tai sao", "tại sao", "why", "khi nao", "khi nào", "when",
                        "tu van", "tư vấn", "goi y", "gợi ý", "huong dan", "hướng dẫn")) {
            return UserIntentDTO.IntentType.INFORMATION_QUERY;
        }

        // Mặc định: tìm kiếm sản phẩm
        return UserIntentDTO.IntentType.PRODUCT_SEARCH;
    }

    /**
     * Trích xuất loại sản phẩm chính
     */
    private String extractProductType(String message) {
        Map<String, String[]> productTypes = Map.of(
            "áo", new String[]{"áo", "shirt", "ao thun", "ao khoac", "ao so mi", "ao len"},
            "quần", new String[]{"quần", "pants", "quan jean", "quan tay", "quan short", "quan dai"},
            "váy", new String[]{"váy", "vay", "dress", "dam", "đầm"},
            "giày", new String[]{"giày", "giay", "shoes", "sneaker", "boot", "dep"},
            "túi", new String[]{"túi", "tui", "bag", "balo", "ba lo", "backpack"},
            "phụ kiện", new String[]{"phu kien", "accessory", "mũ", "nón", "kính", "that lung", "vi"}
        );

        for (Map.Entry<String, String[]> entry : productTypes.entrySet()) {
            if (containsAny(message, entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Trích xuất danh mục từ database
     */
    private String extractCategory(String message) {
        List<Category> allCategories = categoryRepository.findAll();

        for (Category category : allCategories) {
            String catName = normalizeVietnamese(category.getName().toLowerCase());
            if (message.contains(catName)) {
                return category.getName();
            }
        }

        return null;
    }

    /**
     * Trích xuất thương hiệu từ database
     */
    private String extractBrand(String message) {
        List<String> brands = brandRepository.findAll().stream()
                .map(b -> b.getName().toLowerCase())
                .collect(Collectors.toList());

        for (String brand : brands) {
            if (message.contains(normalizeVietnamese(brand))) {
                return brand;
            }
        }

        return null;
    }

    /**
     * Trích xuất màu sắc
     */
    private List<String> extractColors(String message) {
        List<String> colors = new ArrayList<>();

        String[] colorKeywords = {
            "đen", "trắng", "đỏ", "xanh", "vàng", "hồng", "tím", "cam", "nâu", "xám",
            "black", "white", "red", "blue", "yellow", "pink", "purple", "orange", "brown", "gray",
            "den", "trang", "do", "vang", "hong", "tim", "nau", "xam"
        };

        for (String color : colorKeywords) {
            if (message.contains(color)) {
                colors.add(color);
            }
        }

        return colors.isEmpty() ? null : colors;
    }

    /**
     * Trích xuất kích thước
     */
    private List<String> extractSizes(String message) {
        List<String> sizes = new ArrayList<>();

        // Pattern cho size: S, M, L, XL, XXL, số (38, 39, 40...)
        Pattern sizePattern = Pattern.compile("\\b(xs|s|m|l|xl|xxl|xxxl|\\d{2})\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = sizePattern.matcher(message);

        while (matcher.find()) {
            sizes.add(matcher.group(1).toUpperCase());
        }

        // Tìm các từ khóa size
        if (containsAny(message, "size nho", "size nhỏ", "small")) {
            sizes.add("S");
        }
        if (containsAny(message, "size vua", "medium")) {
            sizes.add("M");
        }
        if (containsAny(message, "size lon", "size lớn", "large")) {
            sizes.add("L");
        }

        return sizes.isEmpty() ? null : sizes;
    }

    /**
     * Trích xuất khoảng giá
     */
    private UserIntentDTO.PriceRange extractPriceRange(String message) {
        // Pattern tìm số tiền: 100k, 100000, 1 triệu, 1tr
        Pattern pricePattern = Pattern.compile("(\\d+)\\s*(k|triệu|tr|trieu|000)?");
        Matcher matcher = pricePattern.matcher(message);

        List<Long> prices = new ArrayList<>();
        while (matcher.find()) {
            try {
                long amount = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);

                if (unit != null) {
                    if (unit.equals("k") || unit.equals("000")) {
                        amount *= 1000;
                    } else if (unit.startsWith("tr")) {
                        amount *= 1000000;
                    }
                }

                prices.add(amount);
            } catch (NumberFormatException e) {
                // Ignore invalid numbers
            }
        }

        if (prices.isEmpty()) {
            // Phát hiện khoảng giá theo từ khóa
            if (containsAny(message, "rẻ", "re", "giá rẻ", "cheap", "phu hop", "phù hợp")) {
                return UserIntentDTO.PriceRange.builder()
                        .min(0L)
                        .max(500000L)
                        .build();
            }
            if (containsAny(message, "tầm trung", "tam trung", "medium")) {
                return UserIntentDTO.PriceRange.builder()
                        .min(500000L)
                        .max(2000000L)
                        .build();
            }
            if (containsAny(message, "cao cấp", "cao cap", "luxury", "sang", "đắt", "dat")) {
                return UserIntentDTO.PriceRange.builder()
                        .min(2000000L)
                        .max(Long.MAX_VALUE)
                        .build();
            }
            return null;
        }

        if (prices.size() == 1) {
            // Chỉ có 1 giá: tìm "dưới X" hoặc "trên X"
            if (containsAny(message, "dưới", "duoi", "under", "below", "tối đa", "toi da", "max")) {
                return UserIntentDTO.PriceRange.builder()
                        .min(0L)
                        .max(prices.get(0))
                        .build();
            } else if (containsAny(message, "trên", "tren", "above", "over", "từ", "tu", "from")) {
                return UserIntentDTO.PriceRange.builder()
                        .min(prices.get(0))
                        .max(Long.MAX_VALUE)
                        .build();
            } else {
                // Khoảng +/- 20%
                long price = prices.get(0);
                return UserIntentDTO.PriceRange.builder()
                        .min((long)(price * 0.8))
                        .max((long)(price * 1.2))
                        .build();
            }
        }

        // Có 2 giá trở lên: lấy min và max
        Collections.sort(prices);
        return UserIntentDTO.PriceRange.builder()
                .min(prices.get(0))
                .max(prices.get(prices.size() - 1))
                .build();
    }

    /**
     * Trích xuất giới tính
     */
    private String extractGender(String message) {
        if (containsAny(message, "nam", "man", "men", "boy", "anh", "chàng")) {
            return "Nam";
        }
        if (containsAny(message, "nữ", "nu", "woman", "women", "girl", "chị", "cô")) {
            return "Nữ";
        }
        if (containsAny(message, "unisex", "cả nam và nữ", "ca nam va nu")) {
            return "Unisex";
        }
        return null;
    }

    /**
     * Trích xuất phong cách
     */
    private String extractStyle(String message) {
        Map<String, String[]> styles = Map.of(
            "Thể thao", new String[]{"the thao", "sport", "gym", "chạy", "chay"},
            "Công sở", new String[]{"cong so", "office", "formal", "lịch sự", "lich su"},
            "Dạo phố", new String[]{"dao pho", "casual", "street", "đi chơi", "di choi"},
            "Dự tiệc", new String[]{"du tiec", "party", "event", "sự kiện", "su kien"},
            "Vintage", new String[]{"vintage", "retro", "cổ điển", "co dien"}
        );

        for (Map.Entry<String, String[]> entry : styles.entrySet()) {
            if (containsAny(message, entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Trích xuất từ khóa tìm kiếm
     */
    private String extractSearchKeywords(String message, UserIntentDTO intent) {
        List<String> keywords = new ArrayList<>();

        // PRIORITY 1: Lấy từ khóa từ câu gốc (giữ nguyên chi tiết như "áo thun", "quần jean")
        String[] words = message.split("\\s+");
        List<String> productWords = new ArrayList<>();

        for (String word : words) {
            if (word.length() > 2 && !isStopWord(word) && !isActionWord(word)) {
                productWords.add(word);
            }
        }

        // Nếu có 2-3 từ liên tiếp về sản phẩm (vd: "ao thun"), giữ nguyên
        if (productWords.size() >= 2) {
            keywords.add(String.join(" ", productWords.subList(0, Math.min(3, productWords.size()))));
        } else if (!productWords.isEmpty()) {
            keywords.addAll(productWords);
        }

        // PRIORITY 2: Thêm các thông tin đã phân tích (brand, category nếu có)
        if (intent.getBrand() != null) keywords.add(intent.getBrand());
        if (intent.getCategory() != null) keywords.add(intent.getCategory());
        if (intent.getColors() != null) keywords.addAll(intent.getColors());
        if (intent.getStyle() != null) keywords.add(intent.getStyle());
        if (intent.getGender() != null) keywords.add(intent.getGender());

        // Trả về chuỗi rỗng thay vì null để AiAssistantService xử lý lấy top products
        return keywords.isEmpty() ? "" : String.join(" ", keywords);
    }

    /**
     * Kiểm tra từ có phải action word không (tim, muon, can, cho...)
     */
    private boolean isActionWord(String word) {
        String[] actionWords = {"tim", "tìm", "find", "search", "muon", "muốn", "can", "cần",
                               "cho", "show", "xem", "hien", "hiển", "thi", "có", "co"};
        return Arrays.asList(actionWords).contains(word);
    }

    /**
     * Xác định độ ưu tiên tiêu chí tìm kiếm
     */
    private Map<String, Integer> determinePriority(String message) {
        Map<String, Integer> priority = new HashMap<>();

        // Phân tích vị trí từ khóa trong câu để xác định mức độ quan trọng
        String[] words = message.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            int weight = words.length - i; // Từ đầu câu quan trọng hơn

            if (containsAny(word, "màu", "mau", "color")) {
                priority.put("color", weight * 2);
            }
            if (containsAny(word, "giá", "gia", "price")) {
                priority.put("price", weight * 2);
            }
            if (containsAny(word, "size", "cỡ", "co")) {
                priority.put("size", weight * 2);
            }
        }

        return priority;
    }

    /**
     * Chuẩn hóa tiếng Việt (bỏ dấu)
     */
    private String normalizeVietnamese(String text) {
        String result = text;
        result = result.replaceAll("[áàảãạăắằẳẵặâấầẩẫậ]", "a");
        result = result.replaceAll("[éèẻẽẹêếềểễệ]", "e");
        result = result.replaceAll("[íìỉĩị]", "i");
        result = result.replaceAll("[óòỏõọôốồổỗộơớờởỡợ]", "o");
        result = result.replaceAll("[úùủũụưứừửữự]", "u");
        result = result.replaceAll("[ýỳỷỹỵ]", "y");
        result = result.replaceAll("đ", "d");
        return result;
    }

    /**
     * Kiểm tra chuỗi có chứa bất kỳ từ nào
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
     * Kiểm tra stop word
     */
    private boolean isStopWord(String word) {
        String[] stopWords = {"the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                              "của", "cua", "và", "va", "hoặc", "hoac", "với", "voi", "cho", "từ", "tu"};
        return Arrays.asList(stopWords).contains(word);
    }
}
