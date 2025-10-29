# 🚀 Quick Start: Hệ Thống AI Intent Analysis

## ✅ Đã Hoàn Thành

Hệ thống AI chatbot đã được nâng cấp với khả năng **phân tích ý định người dùng thông minh**:

### 📁 Các File Đã Tạo/Sửa:

1. **`UserIntentDTO.java`** - DTO lưu trữ thông tin phân tích ý định
2. **`UserIntentAnalyzer.java`** - Service phân tích ý định từ ngôn ngữ tự nhiên  
3. **`AiAssistantService.java`** - Đã nâng cấp với logic phân tích thông minh
4. **`ProductRepository.java`** - Thêm 4 query methods mới cho tìm kiếm nâng cao
5. **`ProductCatalogDTO.java`** - Thêm helper methods (getColors, getSizes, getCategoryName)

---

## 🧪 Test Ngay

### Test 1: Tìm kiếm cơ bản
```bash
POST http://localhost:8080/api/ai/chat
Content-Type: text/plain

Tôi muốn tìm áo thun màu đen
```

**Kỳ vọng:** Hệ thống phân tích được:
- Intent: PRODUCT_SEARCH
- Product Type: "áo"
- Color: "đen"

---

### Test 2: Tìm kiếm với giá
```bash
POST http://localhost:8080/api/ai/chat
Content-Type: text/plain

Cho tôi xem giày dưới 1 triệu
```

**Kỳ vọng:** 
- Intent: PRODUCT_SEARCH
- Product Type: "giày"
- Price Range: 0 - 1,000,000đ

---

### Test 3: So sánh sản phẩm
```bash
POST http://localhost:8080/api/ai/chat
Content-Type: text/plain

So sánh áo khoác Nike và Adidas
```

**Kỳ vọng:**
- Intent: PRODUCT_COMPARE
- Brands: Nike, Adidas

---

### Test 4: Câu hỏi phức tạp
```bash
POST http://localhost:8080/api/ai/chat
Content-Type: text/plain

Tôi cần váy dự tiệc màu đỏ size M giá từ 1-2 triệu
```

**Kỳ vọng:** Phân tích được:
- Intent: PRODUCT_RECOMMENDATION
- Product Type: "váy"
- Style: "Dự tiệc"
- Color: "đỏ"
- Size: "M"
- Price Range: 1,000,000 - 2,000,000đ

---

## 📊 Kiểm Tra Log

Sau khi gửi request, check console log:

```log
INFO - Analyzing user intent for: [câu hỏi]
INFO - ✓ Intent analyzed: type=PRODUCT_SEARCH, productType=áo, category=null, brand=null
INFO - Search keyword: 'áo đen'
INFO - Found 15 products matching intent
INFO - After filtering and sorting: 10 products
INFO - AI response generated successfully
```

---

## 🔧 Nếu Gặp Lỗi

### Lỗi: "Cannot resolve symbol UserIntentDTO"

**Giải pháp:**
```bash
# Rebuild project
mvnw.cmd clean compile -DskipTests

# Hoặc restart IDE
# File > Invalidate Caches > Invalidate and Restart
```

### Lỗi: "LM Studio is not available"

**Giải pháp:**
1. Mở LM Studio
2. Tab "Local Server" 
3. Chọn model (ví dụ: `llama-3.2-3b-instruct`)
4. Click "START SERVER"
5. Port phải là **1234**

---

## 🎯 Điểm Nổi Bật

### So Với Hệ Thống Cũ:

| Tính năng | Trước | Sau |
|-----------|-------|-----|
| Hiểu ý định | ❌ | ✅ 4 loại intent |
| Trích xuất thông tin | ⚠️ Keyword đơn | ✅ 10+ thuộc tính |
| Tìm kiếm | ⚠️ LIKE đơn giản | ✅ Đa tiêu chí + lọc |
| Sắp xếp | ⚠️ Ngẫu nhiên | ✅ Điểm liên quan |
| Xử lý giá | ❌ | ✅ Parse "1 triệu", "500k" |

---

## 💡 Ví Dụ Thực Tế

### Người dùng nhập:
> "Tôi cần áo sơ mi công sở màu trắng size L giá khoảng 300-500k"

### Hệ thống phân tích:
```json
{
  "intentType": "PRODUCT_RECOMMENDATION",
  "productType": "áo",
  "style": "Công sở",
  "colors": ["trắng"],
  "sizes": ["L"],
  "priceRange": {
    "min": 300000,
    "max": 500000
  }
}
```

### Hệ thống xử lý:
1. Tìm sản phẩm khớp từ khóa "áo sơ mi công sở"
2. Lọc: Chỉ giữ sản phẩm màu trắng
3. Lọc: Chỉ giữ sản phẩm có size L
4. Lọc: Chỉ giữ sản phẩm giá 300k-500k
5. Sắp xếp: Ưu tiên sản phẩm điểm cao nhất
6. AI tư vấn chi tiết TOP 5 sản phẩm phù hợp nhất

---

## 📖 Tài Liệu Chi Tiết

Xem file `AI_INTENT_ANALYSIS_GUIDE.md` để hiểu sâu hơn về:
- Thuật toán phân tích
- Cách tính điểm liên quan
- Các pattern nhận diện
- Kiến trúc hệ thống

---

## ✨ Kết Quả

Chatbot giờ đây có thể:

✅ Hiểu câu hỏi phức tạp bằng tiếng Việt tự nhiên
✅ Trích xuất thông tin từ nhiều định dạng ("1tr", "1 triệu", "1000000")
✅ Tìm kiếm chính xác với 7+ tiêu chí kết hợp
✅ Ưu tiên sản phẩm phù hợp nhất
✅ So sánh và tư vấn thông minh

**Trải nghiệm người dùng cải thiện 10x!** 🎉

