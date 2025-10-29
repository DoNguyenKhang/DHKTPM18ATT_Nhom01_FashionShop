# Hệ Thống Chống Đồng Thời (Concurrency Control)

## ❌ Vấn Đề Trước Đây

Hệ thống **CHƯA CÓ** cơ chế xử lý đồng thời khi nhiều đơn đặt hàng cùng lúc với số lượng tồn kho còn ít (ví dụ: 1 sản phẩm).

### Kịch Bản Overselling (Bán Vượt Tồn Kho)

**Tình huống:** Sản phẩm A có tồn kho = 1

```
Thời điểm | User 1                | User 2                | Stock DB
----------|----------------------|----------------------|----------
T1        | Đọc stock = 1        |                      | 1
T2        |                      | Đọc stock = 1        | 1
T3        | Kiểm tra: 1 >= 1 ✓   |                      | 1
T4        |                      | Kiểm tra: 1 >= 1 ✓   | 1
T5        | Giảm: stock = 0      |                      | 0
T6        |                      | Giảm: stock = -1 ❌  | -1
```

**Kết quả:** Cả 2 user đều đặt hàng thành công → Overselling!

### Code Cũ (Không An Toàn)

```java
// ❌ Race Condition - Không an toàn
ProductVariant variant = productVariantRepository.findById(variantId).get();

// T1: User 1 và User 2 cùng đọc stock = 1
if (variant.getStock() < quantity) {  // T2: Cả 2 đều pass
    throw new RuntimeException("Insufficient stock");
}

// T3: Cả 2 đều giảm stock
variant.setStock(variant.getStock() - quantity);
productVariantRepository.save(variant);
```

**Vấn đề:** Giữa lúc kiểm tra (`if`) và lúc cập nhật (`setStock`), có thể có thread/request khác cũng đang thực hiện.

---

## ✅ Giải Pháp Đã Triển Khai

### 1. Pessimistic Locking (Khóa Bi Quan)

Lock bản ghi khi đọc để ngăn các transaction khác đọc cùng lúc.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
Optional<ProductVariant> findByIdWithLock(@Param("id") Long id);
```

### 2. Atomic UPDATE Operation

Cập nhật stock trong **1 câu SQL duy nhất** với điều kiện kiểm tra.

```java
@Modifying
@Query("UPDATE ProductVariant pv SET pv.stock = pv.stock - :quantity, pv.updatedAt = CURRENT_TIMESTAMP " +
       "WHERE pv.id = :id AND pv.stock >= :quantity")
int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
```

**Ưu điểm:**
- Database đảm bảo atomic (không thể bị gián đoạn giữa chừng)
- Kiểm tra và cập nhật trong cùng 1 câu lệnh
- Trả về số rows bị ảnh hưởng → biết được thành công hay thất bại

### 3. Code Mới (An Toàn)

```java
// ✅ Thread-Safe với Pessimistic Lock + Atomic Update
ProductVariant variant = productVariantRepository.findByIdWithLock(variantId)
    .orElseThrow(() -> new RuntimeException("Product variant not found"));

// Atomic decrease stock - chỉ 1 transaction thành công
int rowsAffected = productVariantRepository.decreaseStock(variant.getId(), quantity);

if (rowsAffected == 0) {
    // Stock không đủ hoặc variant không tồn tại
    throw new RuntimeException("Insufficient stock for product: " + variant.getProduct().getName());
}
```

---

## 🔄 Luồng Hoạt Động Chi Tiết

### Kịch Bản: 2 User Đặt Hàng Cùng Lúc (Stock = 1)

```
Thời điểm | User 1 Transaction          | User 2 Transaction          | Stock DB
----------|----------------------------|----------------------------|----------
T1        | BEGIN TRANSACTION          |                            | 1
T2        | findByIdWithLock() → LOCK  |                            | 1 (locked)
T3        |                            | BEGIN TRANSACTION          | 1
T4        |                            | findByIdWithLock() → WAIT  | 1 (locked)
T5        | decreaseStock(1) → OK      |                            | 0
T6        | COMMIT → UNLOCK            |                            | 0
T7        |                            | Lock acquired              | 0
T8        |                            | decreaseStock(1) → FAIL    | 0
T9        |                            | rowsAffected = 0 → ERROR   | 0
T10       |                            | ROLLBACK                   | 0
```

**Kết quả:**
- ✅ User 1: Đặt hàng thành công
- ❌ User 2: Nhận thông báo "Insufficient stock"
- ✅ Stock = 0 (đúng)
- ✅ Không có overselling

---

## 🛡️ Các Trường Hợp Được Bảo Vệ

### 1. Tạo Đơn Hàng (createOrder)
- ✅ Sử dụng `findByIdWithLock()` để lock variant
- ✅ Sử dụng `decreaseStock()` để giảm stock atomic
- ✅ Kiểm tra `rowsAffected` để đảm bảo thành công

### 2. Hủy Đơn Hàng (cancelOrder)
- ✅ Sử dụng `increaseStock()` để hoàn trả stock atomic
- ✅ Tránh race condition khi nhiều đơn hủy cùng lúc

### 3. Hoàn Trả (Refund)
- ✅ Tương tự hủy đơn hàng
- ✅ Stock được hoàn trả chính xác

---

## 📊 So Sánh Trước & Sau

| Tiêu Chí                    | Trước (❌)          | Sau (✅)           |
|----------------------------|--------------------|--------------------|
| Race Condition             | Có thể xảy ra      | Được ngăn chặn     |
| Overselling                | Có thể xảy ra      | Không thể xảy ra   |
| Concurrency Level          | Thấp               | Cao                |
| Data Consistency           | Không đảm bảo      | Đảm bảo            |
| Transaction Isolation      | READ_COMMITTED     | PESSIMISTIC_WRITE  |

---

## 🧪 Test Concurrency

### Test Manual

Bạn có thể test bằng cách:

1. Tạo 1 product variant với stock = 1
2. Sử dụng tools như JMeter hoặc Postman với 10 requests đồng thời
3. Chỉ 1 request thành công, 9 requests còn lại báo "Insufficient stock"

### Test với JUnit (Đề xuất)

```java
@Test
@Transactional
void testConcurrentOrders_OnlyOneSucceeds() throws InterruptedException {
    // Setup: Product với stock = 1
    ProductVariant variant = createVariantWithStock(1);
    
    // 10 threads cùng đặt hàng
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(10);
    AtomicInteger successCount = new AtomicInteger(0);
    
    for (int i = 0; i < 10; i++) {
        executor.submit(() -> {
            try {
                orderService.createOrder(userId, orderRequest);
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Expected: Insufficient stock
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await();
    
    // Verify: Chỉ 1 đơn hàng thành công
    assertEquals(1, successCount.get());
    
    // Verify: Stock = 0
    ProductVariant updated = variantRepository.findById(variant.getId()).get();
    assertEquals(0, updated.getStock());
}
```

---

## ⚡ Performance Impact

### Pessimistic Locking
- **Pros:** Đảm bảo data consistency tuyệt đối
- **Cons:** Có thể làm giảm throughput khi có nhiều concurrent requests
- **Trade-off:** An toàn dữ liệu > Performance (phù hợp với e-commerce)

### Tối Ưu Hóa
- Lock chỉ áp dụng trên variant đang được đặt hàng
- Lock được giải phóng ngay sau khi transaction hoàn thành
- Không ảnh hưởng đến các operations khác (đọc, tìm kiếm, etc.)

---

## 🎯 Kết Luận

Hệ thống **ĐÃ ĐƯỢC NÂNG CẤP** với cơ chế chống đồng thời hoàn chỉnh:

✅ **Ngăn chặn overselling** khi stock = 1 và nhiều người đặt cùng lúc  
✅ **Đảm bảo data consistency** trong mọi trường hợp  
✅ **Atomic operations** cho cả giảm và tăng stock  
✅ **Pessimistic locking** để serialize các operations quan trọng  
✅ **Thread-safe** và **transaction-safe**  

---

## 📝 Ghi Chú Cho Developer

1. **Luôn dùng `@Transactional`** cho các operations thay đổi stock
2. **Không dùng `findById()`** thông thường trong create order - phải dùng `findByIdWithLock()`
3. **Kiểm tra `rowsAffected`** sau mỗi atomic operation
4. **Flush EntityManager** sau atomic operation để đảm bảo refresh data
5. **Test concurrent scenarios** trước khi deploy production

---

**Ngày cập nhật:** 21/10/2025  
**Version:** 2.0  
**Status:** ✅ Production Ready

