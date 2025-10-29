# Redis Cache - LocalDateTime Serialization Fix

## ❌ Lỗi đã khắc phục:
```
SerializationException: Could not write JSON: Java 8 date/time type `java.time.LocalDateTime` not supported by default
```

## 🔍 Nguyên nhân:
Redis cache sử dụng `GenericJackson2JsonRedisSerializer` để serialize các đối tượng Java thành JSON. Mặc định, Jackson không biết cách serialize các kiểu date/time của Java 8 (`LocalDateTime`, `LocalDate`, `LocalTime`, etc.) mà không có module `jackson-datatype-jsr310`.

## ✅ Giải pháp đã áp dụng:

### 1. Cấu hình ObjectMapper với JavaTimeModule
Trong file `CacheConfig.java`, đã tạo một `ObjectMapper` tùy chỉnh:

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

**Giải thích:**
- `registerModule(new JavaTimeModule())` - Đăng ký module hỗ trợ Java 8 date/time
- `disable(WRITE_DATES_AS_TIMESTAMPS)` - Serialize dates thành chuỗi ISO-8601 thay vì timestamps

### 2. Sử dụng ObjectMapper cho Redis Serializer
```java
GenericJackson2JsonRedisSerializer serializer = 
    new GenericJackson2JsonRedisSerializer(objectMapper);
```

### 3. Áp dụng cho tất cả cache configurations
Serializer đã được áp dụng cho tất cả các cache:
- products
- productVariants
- productReviews
- categories
- brands
- productSearch
- aiResponses
- etc.

## 📊 Kết quả:

### Trước khi fix:
- ❌ Redis cache throw SerializationException khi cache Product/Category/Brand
- ❌ API trả về lỗi 500 Internal Server Error
- ❌ Không thể cache được bất kỳ object nào có LocalDateTime

### Sau khi fix:
- ✅ Redis cache serialize LocalDateTime thành chuỗi ISO-8601
- ✅ API hoạt động bình thường và data được cache
- ✅ Hiệu suất được cải thiện đáng kể

## 🧪 Kiểm tra:

### 1. Test API với cache
```bash
# Lần đầu tiên (chưa có cache) - chậm hơn
GET http://localhost:8080/api/products

# Lần thứ 2 (có cache) - nhanh hơn rất nhiều
GET http://localhost:8080/api/products
```

### 2. Kiểm tra data trong Redis
Nếu bạn có Redis CLI:
```bash
redis-cli -p 6380
> KEYS *
> GET "products::all_0_10"
```

Bạn sẽ thấy LocalDateTime được serialize như:
```json
{
  "createdAt": "2025-10-14T16:29:55.996",
  "updatedAt": "2025-10-14T16:29:55.996"
}
```

## 🎯 Cache TTL (Time To Live):

| Cache Name | TTL | Mục đích |
|------------|-----|----------|
| products | 1 giờ | Thông tin sản phẩm thay đổi ít |
| categories | 1 giờ | Danh mục thay đổi rất ít |
| brands | 1 giờ | Thương hiệu thay đổi rất ít |
| productVariants | 5 phút | Stock thay đổi thường xuyên |
| productReviews | 1 giờ | Review mới không cần real-time |
| productSearch | 15 phút | Kết quả tìm kiếm |
| aiResponses | 30 phút | AI responses |

## 🔄 Cache Eviction:

Cache tự động bị xóa khi:
- ✓ Tạo mới product/variant/review → `@CacheEvict(value = "products", allEntries = true)`
- ✓ Cập nhật product/variant/review → `@CacheEvict(value = "products", allEntries = true)`
- ✓ Xóa product/variant/review → `@CacheEvict(value = "products", allEntries = true)`
- ✓ Hết thời gian TTL

## 📝 Lưu ý quan trọng:

### 1. ISO-8601 Date Format
Dates được serialize theo chuẩn ISO-8601:
- `LocalDateTime`: "2025-10-14T16:29:55.996"
- `LocalDate`: "2025-10-14"
- `LocalTime`: "16:29:55.996"

### 2. Timezone
LocalDateTime không có timezone information. Nếu cần timezone, sử dụng `ZonedDateTime`.

### 3. Backward Compatibility
Nếu có data cũ trong cache với format khác, cần clear cache:
```bash
redis-cli -p 6380 FLUSHALL
```

Hoặc restart Redis:
```bash
net stop Redis
net start Redis
```

## 🚀 Performance Impact:

### Benchmark (ước lượng):
- **Không cache**: ~200-500ms (query từ database)
- **Có cache**: ~5-20ms (đọc từ Redis)
- **Improvement**: 10-100x nhanh hơn

### Memory Usage:
- Redis sẽ sử dụng thêm RAM để lưu cache
- Monitor với: `redis-cli -p 6380 INFO memory`
- Nếu RAM cao, giảm TTL hoặc tăng RAM cho Redis

## 🐛 Troubleshooting:

### Nếu vẫn gặp lỗi serialization:
1. Clear Redis cache: `redis-cli -p 6380 FLUSHALL`
2. Restart application
3. Kiểm tra log xem object nào gây lỗi
4. Đảm bảo tất cả fields trong DTO đều serializable

### Nếu cache không hoạt động:
1. Kiểm tra Redis đang chạy: `netstat -an | find ":6380"`
2. Kiểm tra log có lỗi Redis connection không
3. Test Redis connection: `redis-cli -p 6380 PING`

## 🎓 Best Practices:

1. **Cache read-heavy endpoints** - Chỉ cache những API được đọc nhiều
2. **Appropriate TTL** - Chọn TTL phù hợp với tần suất thay đổi data
3. **Cache eviction** - Luôn evict cache khi data thay đổi
4. **Monitor cache hit/miss** - Theo dõi hiệu quả của cache
5. **Handle cache failures gracefully** - App vẫn hoạt động nếu Redis down

## 📚 Tài liệu tham khảo:
- Jackson JSR310: https://github.com/FasterXML/jackson-modules-java8
- Spring Cache: https://docs.spring.io/spring-framework/reference/integration/cache.html
- Redis: https://redis.io/docs/

