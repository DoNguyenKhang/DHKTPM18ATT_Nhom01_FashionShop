# 🚀 Quick Start - AI Product Consultant

## Bước 1: Khởi động các services cần thiết

### 1.1 Khởi động Redis
```bash
# Windows (nếu cài Redis)
redis-server

# Hoặc sử dụng Docker
docker run -d -p 6379:6379 redis:latest
```

### 1.2 Khởi động LM Studio
1. Mở LM Studio
2. Vào tab "Local Server"
3. Chọn model: `llama-3.2-1b-instruct`
4. Click "START SERVER"
5. Đảm bảo port là 1234

### 1.3 Khởi động Spring Boot Application
```bash
cd D:\Project\fashion
mvn spring-boot:run
```

## Bước 2: Test API với 5 Use Cases Phổ Biến

### Use Case 1: Tìm 5 sản phẩm áo
```bash
curl "http://localhost:8080/api/ai/search?keyword=áo&limit=5"
```

**Kết quả mong đợi:**
```json
{
  "response": "Tìm thấy 5 sản phẩm áo phù hợp:\n1. Áo Sơ Mi Aristino - 650,000₫ (màu xanh, trắng; size M, L, XL)\n2. Áo Polo Routine - 450,000₫ (màu đen, xanh; size S, M, L)\n...",
  "model": "llama-3.2-1b-instruct",
  "timestamp": 1729000000000
}
```

### Use Case 2: Tư vấn 5 sản phẩm theo nhu cầu
```bash
curl -X POST http://localhost:8080/api/ai/chat/product \
  -H "Content-Type: application/json" \
  -d '"Tôi cần 5 áo sơ mi đi làm, màu nhã nhặn, giá 500-700k"'
```

**Kết quả mong đợi:**
AI sẽ phân tích và gợi ý 5 sản phẩm phù hợp nhất với tiêu chí.

### Use Case 3: Xem 5 sản phẩm của thương hiệu
```bash
curl "http://localhost:8080/api/ai/brand/1?question=Gợi ý 5 sản phẩm bán chạy&limit=5"
```

### Use Case 4: Xem 5 sản phẩm trong danh mục
```bash
curl "http://localhost:8080/api/ai/category/5?question=Top 5 sản phẩm nổi bật&limit=5"
```

### Use Case 5: Chat tự do để được gợi ý sản phẩm
```bash
curl -X POST http://localhost:8080/api/ai/chat/product \
  -H "Content-Type: application/json" \
  -d '"Giới thiệu 5 sản phẩm áo đẹp nhất của shop"'
```

## Bước 3: Test từ Frontend

### HTML + JavaScript Example
```html
<!DOCTYPE html>
<html>
<head>
    <title>AI Product Consultant Demo</title>
</head>
<body>
    <h1>Tư Vấn Sản Phẩm AI</h1>
    
    <input id="searchKeyword" placeholder="Nhập từ khóa tìm kiếm...">
    <button onclick="searchProducts()">Tìm 5 sản phẩm</button>
    
    <div id="result"></div>

    <script>
        async function searchProducts() {
            const keyword = document.getElementById('searchKeyword').value;
            const response = await fetch(
                `http://localhost:8080/api/ai/search?keyword=${encodeURIComponent(keyword)}&limit=5`
            );
            const data = await response.json();
            document.getElementById('result').innerHTML = 
                `<pre>${data.response}</pre>`;
        }
    </script>
</body>
</html>
```

## Bước 4: Kiểm tra Performance

### Lần đầu (Cold Start - Không có cache)
- Response time: ~2-5 giây (tùy model AI)
- Query database: Nhiều queries

### Lần thứ 2 trở đi (Warm - Có cache)
- Response time: < 500ms
- Query database: 0 queries (lấy từ Redis)

### Kiểm tra Cache
```bash
# Kết nối Redis CLI
redis-cli

# Xem các keys đã cache
KEYS *

# Xem nội dung cache của search "áo"
GET "productSearch::áo_5"

# Xem AI response cache
KEYS aiResponses*
```

## Troubleshooting

### Lỗi: Cannot resolve symbol 'ProductCatalogDTO'
**Giải pháp:**
```bash
# Rebuild project
mvn clean compile -DskipTests

# Hoặc trong IntelliJ IDEA:
# File -> Invalidate Caches and Restart
```

### Lỗi: LM Studio not available
**Giải pháp:**
1. Kiểm tra LM Studio đã start server chưa
2. Test trực tiếp: `curl http://localhost:1234/v1/models`
3. Kiểm tra port 1234 có bị chiếm không

### Lỗi: Redis connection failed
**Giải pháp:**
```bash
# Test Redis
redis-cli ping
# Kết quả mong đợi: PONG
```

## Tips & Best Practices

1. **Luôn test với limit=5** để có kết quả vừa đủ, không quá nhiều
2. **Sử dụng cache hiệu quả**: Những câu hỏi giống nhau sẽ được trả lời ngay lập tức
3. **Monitor Redis**: Theo dõi cache hit rate để tối ưu TTL
4. **Clear cache khi cần**: 
   ```bash
   redis-cli FLUSHDB
   ```

## Kiến Trúc Tổng Quan

```
User Request "Tìm 5 áo sơ mi"
         ↓
    Controller
         ↓
  AiAssistantService
         ↓
         ├─→ Check Redis Cache
         │   ├─ HIT → Return ngay (< 500ms)
         │   └─ MISS → Continue
         ↓
  CatalogCacheService
         ↓
    Query Database
         ↓
  Build AI Context
         ↓
    LM Studio AI
         ↓
  Cache Response
         ↓
    Return to User
```

## Metrics để Monitor

- **Cache Hit Rate**: > 70% là tốt
- **Response Time**: 
  - Cold: < 5s
  - Warm: < 500ms
- **Database Queries**: Giảm 90% nhờ cache

---

**Prepared by:** AI Development Team  
**Version:** 1.0.0  
**Date:** 2025-10-15

