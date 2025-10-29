# Hướng Dẫn: Hệ Thống Phân Tích Ý Định Người Dùng (AI Intent Analysis)

## Tổng Quan

Hệ thống AI chatbot đã được **nâng cấp đáng kể** để hiểu rõ hơn ý định của người dùng khi họ nhập thông tin. Thay vì chỉ tìm kiếm từ khóa đơn giản, hệ thống bây giờ có khả năng:

✅ **Phân tích ý định người dùng** - Hiểu người dùng muốn tìm gì, so sánh gì, hay hỏi thông tin gì
✅ **Trích xuất thông tin chi tiết** - Tự động nhận diện: loại sản phẩm, danh mục, thương hiệu, màu sắc, kích thước, giá cả, phong cách
✅ **Tìm kiếm thông minh** - Truy vấn database với nhiều tiêu chí kết hợp
✅ **Lọc và sắp xếp kết quả** - Ưu tiên sản phẩm phù hợp nhất với nhu cầu người dùng

---

## Các Thành Phần Mới

### 1. **UserIntentDTO** (`dto/UserIntentDTO.java`)
DTO chứa kết quả phân tích ý định người dùng:

```java
public class UserIntentDTO {
    // Câu hỏi gốc và đã chuẩn hóa
    private String originalMessage;
    private String normalizedMessage;
    
    // Loại ý định: PRODUCT_SEARCH, PRODUCT_RECOMMENDATION, PRODUCT_COMPARE, ...
    private IntentType intentType;
    
    // Thông tin sản phẩm được trích xuất
    private String productType;      // "áo", "quần", "váy", "giày"...
    private String category;         // Danh mục cụ thể từ database
    private String brand;            // Thương hiệu từ database
    
    // Thuộc tính sản phẩm
    private List<String> colors;     // Màu sắc: ["đen", "trắng"]
    private List<String> sizes;      // Kích thước: ["M", "L", "XL"]
    private PriceRange priceRange;   // Khoảng giá: min-max
    private String gender;           // "Nam", "Nữ", "Unisex"
    private String style;            // "Thể thao", "Công sở", "Dạo phố"
    
    // Từ khóa tổng hợp và độ ưu tiên
    private String searchKeywords;
    private Map<String, Integer> priority;
}
```

### 2. **UserIntentAnalyzer** (`services/UserIntentAnalyzer.java`)
Service phân tích ý định người dùng từ câu hỏi tự nhiên:

#### Các phương thức chính:
- `analyzeIntent(String userMessage)` - Phân tích toàn diện ý định
- `detectIntentType()` - Xác định loại ý định
- `extractProductType()` - Trích xuất loại sản phẩm
- `extractCategory()` - Tìm danh mục từ database
- `extractBrand()` - Tìm thương hiệu từ database
- `extractColors()` - Nhận diện màu sắc
- `extractSizes()` - Nhận diện kích thước
- `extractPriceRange()` - Phân tích khoảng giá
- `extractGender()` - Xác định giới tính
- `extractStyle()` - Nhận diện phong cách

### 3. **AiAssistantService** (Đã nâng cấp)
Service xử lý AI được cải thiện với các tính năng mới:

#### Luồng xử lý mới:
```
Câu hỏi người dùng
    ↓
Phân tích ý định (UserIntentAnalyzer)
    ↓
Xác định loại intent
    ↓
├─ PRODUCT_SEARCH → searchProductsByIntent()
├─ PRODUCT_RECOMMENDATION → searchProductsByIntent()
├─ PRODUCT_COMPARE → compareProductsByIntent()
└─ INFORMATION_QUERY → chatWithCatalogContext()
    ↓
Tìm kiếm nâng cao (searchProductsAdvanced)
    ↓
Lọc theo tiêu chí (filterByIntent)
    ↓
Sắp xếp theo độ liên quan (sortByRelevance)
    ↓
Tạo prompt thông minh (buildIntelligentMessage)
    ↓
Gọi AI với context chi tiết
    ↓
Trả về kết quả
```

### 4. **ProductRepository** (Đã mở rộng)
Thêm các query methods mới để hỗ trợ tìm kiếm nâng cao:

```java
// Tìm kiếm với nhiều tiêu chí
Page<Product> searchAdvanced(keyword, categoryName, brandName, pageable);

// Tìm theo khoảng giá
Page<Product> findByPriceRange(minPrice, maxPrice, pageable);

// Tìm kiếm kết hợp keyword và giá
Page<Product> searchWithPriceRange(keyword, minPrice, maxPrice, pageable);

// Tìm kiếm toàn diện (keyword, category, brand, color, size, price)
Page<Product> searchComprehensive(keyword, categoryName, brandName, 
                                   colorName, sizeName, minPrice, maxPrice, pageable);
```

### 5. **ProductCatalogDTO** (Đã cải thiện)
Thêm các phương thức helper:

```java
String getColors()           // Lấy danh sách màu sắc có sẵn
String getSizes()            // Lấy danh sách size có sẵn
String getCategoryName()     // Lấy tên category đầu tiên
boolean isPriceInRange()     // Kiểm tra giá trong khoảng
```

---

## Ví Dụ Sử Dụng

### Ví dụ 1: Tìm kiếm cơ bản
**Input:**
```
"Tôi muốn tìm áo thun màu đen"
```

**Phân tích:**
- Intent Type: PRODUCT_SEARCH
- Product Type: "áo"
- Colors: ["đen"]

**Kết quả:** Hệ thống tìm tất cả áo thun màu đen và trả về danh sách chi tiết

---

### Ví dụ 2: Tìm kiếm theo giá
**Input:**
```
"Cho tôi xem quần jean dưới 500k"
```

**Phân tích:**
- Intent Type: PRODUCT_SEARCH
- Product Type: "quần"
- Keywords: "jean"
- Price Range: 0 - 500,000đ

**Kết quả:** Lọc quần jean có giá dưới 500k

---

### Ví dụ 3: Tìm kiếm theo thương hiệu và phong cách
**Input:**
```
"Có áo khoác thể thao Nike không?"
```

**Phân tích:**
- Intent Type: PRODUCT_SEARCH
- Product Type: "áo"
- Brand: "Nike" (từ database)
- Style: "Thể thao"

**Kết quả:** Tìm áo khoác Nike phong cách thể thao

---

### Ví dụ 4: So sánh sản phẩm
**Input:**
```
"So sánh giữa áo khoác Adidas và Nike"
```

**Phân tích:**
- Intent Type: PRODUCT_COMPARE
- Product Type: "áo"
- Keywords: "Adidas", "Nike"

**Kết quả:** AI so sánh chi tiết về giá, chất lượng, màu sắc, size giữa 2 thương hiệu

---

### Ví dụ 5: Tư vấn phức tạp
**Input:**
```
"Tôi cần váy dự tiệc màu đỏ, size M, tầm giá 1-2 triệu"
```

**Phân tích:**
- Intent Type: PRODUCT_RECOMMENDATION
- Product Type: "váy"
- Style: "Dự tiệc"
- Colors: ["đỏ"]
- Sizes: ["M"]
- Price Range: 1,000,000 - 2,000,000đ

**Kết quả:** Tìm váy dự tiệc đáp ứng TẤT CẢ các tiêu chí và sắp xếp theo độ phù hợp

---

## Thuật Toán Tính Điểm Liên Quan

Hệ thống tính điểm cho mỗi sản phẩm dựa trên mức độ khớp với ý định:

```
Điểm = 0

+ 50 điểm: Khớp thương hiệu
+ 40 điểm: Khớp danh mục
+ 30 điểm: Khớp loại sản phẩm
+ 20 điểm: Khớp màu sắc
+ 20 điểm: Khớp kích thước
+ 10 điểm: Trong khoảng giá
```

Sản phẩm có điểm cao nhất sẽ được ưu tiên hiển thị.

---

## Các Loại Intent Được Hỗ Trợ

### 1. PRODUCT_SEARCH
Từ khóa nhận diện: "tìm", "find", "search", "cho tôi", "muốn", "cần"

**Xử lý:** Tìm kiếm và trả về danh sách sản phẩm phù hợp

---

### 2. PRODUCT_RECOMMENDATION
Từ khóa nhận diện: "nên", "tư vấn", "gợi ý", "recommend", "phù hợp"

**Xử lý:** Tìm và phân tích để đưa ra gợi ý cá nhân hóa

---

### 3. PRODUCT_COMPARE
Từ khóa nhận diện: "so sánh", "compare", "khác nhau", "giống", "tương tự"

**Xử lý:** Tìm các sản phẩm và so sánh chi tiết

---

### 4. INFORMATION_QUERY
Từ khóa nhận diện: "thế nào", "how", "là gì", "what", "tại sao", "why"

**Xử lý:** Trả lời câu hỏi thông tin chung

---

## Cải Thiện So Với Hệ Thống Cũ

| Tính năng | Cũ | Mới |
|-----------|-----|-----|
| Phân tích ý định | ❌ Không có | ✅ 4 loại intent |
| Trích xuất thông tin | ⚠️ Cơ bản | ✅ 10+ thuộc tính |
| Tìm kiếm | ⚠️ Keyword đơn | ✅ Đa tiêu chí |
| Lọc kết quả | ❌ Không có | ✅ Theo giá, màu, size |
| Sắp xếp | ⚠️ Mặc định | ✅ Theo độ liên quan |
| Hiểu ngôn ngữ tự nhiên | ⚠️ Hạn chế | ✅ Tiếng Việt + English |
| Xử lý giá | ❌ Không có | ✅ Khoảng giá thông minh |

---

## Cách Test Hệ Thống

### 1. Kiểm tra API endpoint:
```bash
POST http://localhost:8080/api/ai/chat
Content-Type: text/plain

Tôi muốn tìm áo thun màu đen size M dưới 300k
```

### 2. Test từ giao diện web:
- Mở `/ai-chatbot.html`
- Nhập các câu hỏi mẫu ở trên
- Quan sát kết quả trả về

### 3. Kiểm tra log để debug:
```log
INFO - Analyzing user intent for: Tôi muốn tìm áo thun màu đen
INFO - ✓ Intent analyzed: type=PRODUCT_SEARCH, productType=áo, colors=[đen]
INFO - Search keyword: 'áo đen'
INFO - Found 15 products matching intent
INFO - After filtering and sorting: 10 products
```

---

## Rebuild Project

Sau khi thêm các file mới, cần rebuild project:

```bash
# Từ thư mục gốc project
mvnw.cmd clean install -DskipTests

# Hoặc
mvn clean install -DskipTests
```

**Lưu ý:** IDE có thể hiển thị lỗi tạm thời cho UserIntentAnalyzer. Hãy:
1. Build project bằng Maven (đã build thành công)
2. Restart IDE hoặc Invalidate Caches
3. Các lỗi sẽ tự biến mất

---

## Tối Ưu Hóa Trong Tương Lai

- [ ] Machine Learning để cải thiện phân tích intent
- [ ] Hỗ trợ synonym (từ đồng nghĩa)
- [ ] Lưu lịch sử tìm kiếm để cá nhân hóa
- [ ] Tích hợp voice search
- [ ] A/B testing các thuật toán ranking

---

## Kết Luận

Hệ thống AI chatbot giờ đây **thông minh hơn rất nhiều** trong việc hiểu ý định người dùng. Thay vì chỉ tìm kiếm từ khóa đơn thuần, nó có thể:

✅ Hiểu câu hỏi phức tạp với nhiều tiêu chí
✅ Trích xuất thông tin từ ngôn ngữ tự nhiên
✅ Tìm kiếm chính xác với nhiều điều kiện
✅ Lọc và sắp xếp kết quả theo độ liên quan
✅ Đưa ra gợi ý và so sánh thông minh

**Trải nghiệm người dùng được cải thiện đáng kể!**

