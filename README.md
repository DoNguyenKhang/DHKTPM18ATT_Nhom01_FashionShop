# Fashion Shop Backend API

Hệ thống Backend cho website bán quần áo thời trang sử dụng Spring Boot, JPA, MariaDB và JWT Security.

## Công nghệ sử dụng

- **Java 17**
- **Spring Boot 3.5.6**
- **Spring Data JPA**
- **Spring Security + JWT**
- **MariaDB**
- **Lombok**
- **Maven**

## Cấu trúc Database

Database được thiết kế theo mô hình RBAC (Role-Based Access Control) với các bảng chính:
- Users, Roles, Permissions
- Products, Product Variants, Categories, Brands
- Orders, Order Items, Payments, Shipments
- Carts, Cart Items
- Coupons, Inventory Movements
- Audit Logs, Refresh Tokens

## Cài đặt

### 1. Cấu hình Database

Tạo database MariaDB:
```sql
CREATE DATABASE fashion_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Cấu hình application.properties

Cập nhật file `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/fashion_shop
spring.datasource.username=root
spring.datasource.password=your_password
jwt.secret=your-secret-key-minimum-256-bits-for-HS256-algorithm-security
```

### 3. Build và chạy ứng dụng

```bash
# Build project
mvnw clean install

# Chạy ứng dụng
mvnw spring-boot:run
```

Server sẽ chạy tại: `http://localhost:8080`

## Vai trò (Roles)

Hệ thống có 4 vai trò chính:

1. **ADMIN** - Quản lý (toàn quyền)
   - Tất cả các quyền trên hệ thống

2. **STAFF_PRODUCT** - Nhân viên quản lý sản phẩm
   - Tạo, sửa, xóa sản phẩm
   - Quản lý tồn kho

3. **STAFF_SALES** - Nhân viên bán hàng
   - Xem và cập nhật đơn hàng
   - Xử lý giao dịch

4. **CUSTOMER** - Khách hàng
   - Mua sắm, đặt hàng
   - Quản lý giỏ hàng

## API Endpoints

### Authentication

#### Đăng ký
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "password123",
  "fullName": "Nguyen Van A",
  "phone": "0123456789"
}
```

#### Đăng nhập
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "password123"
}

Response:
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "email": "customer@example.com",
    "fullName": "Nguyen Van A",
    "roles": ["CUSTOMER"]
  }
}
```

#### Đăng xuất
```http
POST /api/auth/logout
Authorization: Bearer {accessToken}
```

### Products (Public)

#### Lấy danh sách sản phẩm
```http
GET /api/products?page=0&size=20&sortBy=id&sortDirection=DESC
```

#### Lấy chi tiết sản phẩm
```http
GET /api/products/{id}
GET /api/products/slug/{slug}
```

#### Tìm kiếm sản phẩm
```http
GET /api/products/search?keyword=ao&page=0&size=20
```

#### Lọc theo danh mục
```http
GET /api/products/category/{categoryId}?page=0&size=20
```

#### Lọc theo thương hiệu
```http
GET /api/products/brand/{brandId}?page=0&size=20
```

### Products Management (ADMIN, STAFF_PRODUCT)

#### Tạo sản phẩm
```http
POST /api/products
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "name": "Áo thun nam",
  "slug": "ao-thun-nam",
  "description": "Áo thun cotton cao cấp",
  "brandId": 1,
  "categoryIds": [1, 2],
  "material": "Cotton 100%",
  "origin": "Vietnam",
  "isActive": true
}
```

#### Cập nhật sản phẩm
```http
PUT /api/products/{id}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

#### Xóa sản phẩm (soft delete)
```http
DELETE /api/products/{id}
Authorization: Bearer {accessToken}
```

### Product Variants

#### Tạo biến thể sản phẩm
```http
POST /api/product-variants
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "productId": 1,
  "sku": "AT-001-RED-M",
  "colorId": 1,
  "sizeId": 2,
  "price": 199000,
  "compareAtPrice": 299000,
  "stock": 100,
  "isActive": true
}
```

### Cart (CUSTOMER)

#### Lấy giỏ hàng
```http
GET /api/cart
Authorization: Bearer {accessToken}
```

#### Thêm vào giỏ hàng
```http
POST /api/cart/items
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "variantId": 1,
  "quantity": 2
}
```

#### Cập nhật số lượng
```http
PUT /api/cart/items/{itemId}?quantity=3
Authorization: Bearer {accessToken}
```

#### Xóa sản phẩm khỏi giỏ
```http
DELETE /api/cart/items/{itemId}
Authorization: Bearer {accessToken}
```

#### Xóa toàn bộ giỏ hàng
```http
DELETE /api/cart
Authorization: Bearer {accessToken}
```

### Orders (CUSTOMER)

#### Tạo đơn hàng
```http
POST /api/orders
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "items": [
    {
      "variantId": 1,
      "quantity": 2
    }
  ],
  "shipName": "Nguyen Van A",
  "shipPhone": "0123456789",
  "shipLine1": "123 Nguyen Trai",
  "shipCity": "Ho Chi Minh",
  "shipCountry": "Vietnam",
  "couponCode": "SALE20",
  "note": "Giao giờ hành chính"
}
```

#### Xem đơn hàng của tôi
```http
GET /api/orders/my?page=0&size=10
Authorization: Bearer {accessToken}
```

#### Hủy đơn hàng
```http
POST /api/orders/{id}/cancel
Authorization: Bearer {accessToken}
```

### Orders Management (ADMIN, STAFF_SALES)

#### Xem tất cả đơn hàng
```http
GET /api/orders?page=0&size=10
Authorization: Bearer {accessToken}
```

#### Lọc theo trạng thái
```http
GET /api/orders/status/PENDING?page=0&size=10
Authorization: Bearer {accessToken}
```

#### Cập nhật trạng thái đơn hàng
```http
PUT /api/orders/{id}/status?status=CONFIRMED
Authorization: Bearer {accessToken}
```

### Categories

#### Lấy tất cả danh mục
```http
GET /api/categories
```

#### Lấy danh mục gốc
```http
GET /api/categories/root
```

#### Lấy danh mục con
```http
GET /api/categories/{id}/children
```

### Brands

#### Lấy tất cả thương hiệu
```http
GET /api/brands
```

## Order Status Flow

1. **PENDING** - Đơn hàng mới tạo
2. **CONFIRMED** - Đã xác nhận
3. **PACKING** - Đang đóng gói
4. **SHIPPING** - Đang giao hàng
5. **COMPLETED** - Hoàn thành
6. **CANCELLED** - Đã hủy
7. **REFUNDED** - Đã hoàn tiền

## Tính năng chính

### 1. Authentication & Authorization
- Đăng ký, đăng nhập với JWT
- Phân quyền dựa trên RBAC
- Refresh token rotation
- Token blacklist

### 2. Product Management
- CRUD sản phẩm với nhiều biến thể (màu sắc, kích thước)
- Quản lý tồn kho tự động
- Tìm kiếm và lọc sản phẩm
- Upload hình ảnh sản phẩm

### 3. Shopping Cart
- Thêm, sửa, xóa sản phẩm trong giỏ
- Tự động tính tổng tiền
- Kiểm tra tồn kho

### 4. Order Processing
- Tạo đơn hàng từ giỏ hàng
- Áp dụng mã giảm giá
- Snapshot thông tin giao hàng
- Tự động trừ tồn kho
- Tracking đơn hàng

### 5. Inventory Management
- Ghi nhận nhập/xuất kho
- Lịch sử biến động tồn kho
- Audit log

### 6. Coupon System
- Mã giảm giá theo % hoặc số tiền cố định
- Giới hạn số lần sử dụng
- Điều kiện đơn hàng tối thiểu

## Security

- Password được mã hóa bằng BCrypt
- JWT token cho authentication
- CORS configuration
- Method-level security với @PreAuthorize
- Input validation

## Error Handling

API trả về error response với format:
```json
{
  "status": 400,
  "message": "Error message",
  "timestamp": "2025-10-05T10:30:00"
}
```

## Logging

- SQL queries được log ra console (development)
- Audit log cho các thao tác quan trọng

## License

MIT License

