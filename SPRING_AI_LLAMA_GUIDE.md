# Hướng Dẫn Tích Hợp Spring AI với Llama 3.2-1B

## Tổng Quan

Hệ thống đã được tích hợp Spring AI sử dụng mô hình Llama 3.2-1B Instruct chạy trên local server (http://127.0.0.1:1234). Spring AI sử dụng thư viện OpenAI API để kết nối với Llama vì chúng dùng chung chuẩn API.

## Cài Đặt

### 1. Dependencies Đã Thêm

Trong `pom.xml`:
- `spring-ai-openai-spring-boot-starter`: Thư viện Spring AI với OpenAI API
- `spring-ai-bom`: Bill of Materials cho Spring AI version 1.0.0-M3
- Repository Spring Milestones để tải Spring AI

### 2. Cấu Hình

Trong `application.properties`:
```properties
# Spring AI - Llama 3.2 Configuration
spring.ai.openai.api-key=not-needed
spring.ai.openai.base-url=http://127.0.0.1:1234/v1
spring.ai.openai.chat.options.model=llama-3.2-1b-instruct
spring.ai.openai.chat.options.temperature=0.7
spring.ai.openai.chat.options.max-tokens=500
```

**Lưu ý**: 
- `api-key` có thể để giá trị bất kỳ vì local server không yêu cầu
- `base-url` trỏ đến local server của bạn
- `temperature=0.7`: Độ sáng tạo (0.0-1.0)
- `max-tokens=500`: Số token tối đa trong response

## Các Thành Phần Đã Tạo

### 1. DTOs
- `AiChatRequest.java`: Request với message và context
- `AiChatResponse.java`: Response với câu trả lời, model name và timestamp

### 2. Service Layer
**`AiAssistantService.java`**: Service chính với các phương thức:
- `chat(String message)`: Chat đơn giản
- `chatWithContext(AiChatRequest request)`: Chat với ngữ cảnh bổ sung
- `suggestProducts(String userPreference)`: Gợi ý sản phẩm thời trang
- `adviseSizeGuide(String customerInfo)`: Tư vấn size áo
- `answerOrderQuestion(String orderContext, String question)`: Trả lời câu hỏi về đơn hàng

### 3. Controllers
**`AiAssistantController.java`**: REST API endpoints:
- `POST /api/ai/chat`: Chat đơn giản
- `POST /api/ai/chat/context`: Chat với ngữ cảnh
- `GET /api/ai/suggest-products?preference={text}`: Gợi ý sản phẩm
- `GET /api/ai/size-guide?info={text}`: Tư vấn size
- `POST /api/ai/order-question?question={text}&orderContext={text}`: Câu hỏi đơn hàng
- `GET /api/ai/health`: Kiểm tra trạng thái AI service

**`AiChatbotViewController.java`**: Controller cho giao diện web
- `GET /ai-chatbot`: Trang chatbot

### 4. Configuration
**`AiConfiguration.java`**: Cấu hình Spring AI
- Tạo `OpenAiApi` bean kết nối với local Llama server
- Tạo `ChatModel` bean với các options

### 5. Frontend
**`ai-chatbot.html`**: Giao diện chatbot đẹp mắt với:
- Chat interface hiện đại
- Quick action buttons (tìm áo, tư vấn size, phong cách, phối đồ)
- Loading indicator
- Responsive design

**`ai-chatbot.js`**: JavaScript xử lý:
- Gửi/nhận tin nhắn
- Hiển thị chat messages
- Gọi API endpoints
- Xử lý lỗi

## Cách Sử Dụng

### 1. Khởi Động Local LLM Server

Đảm bảo Llama 3.2-1B server đang chạy trên `http://127.0.0.1:1234`

### 2. Build Project

```bash
mvnw clean install
```

### 3. Chạy Ứng Dụng

```bash
mvnw spring-boot:run
```

### 4. Truy Cập Chatbot

Mở trình duyệt và truy cập:
```
http://localhost:8080/ai-chatbot
```

### 5. Sử Dụng API

#### Chat đơn giản:
```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d "Tôi muốn tìm áo thun nam màu đen"
```

#### Chat với ngữ cảnh:
```bash
curl -X POST http://localhost:8080/api/ai/chat/context \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Áo nào phù hợp với tôi?",
    "context": "Khách hàng nam, 25 tuổi, thích phong cách casual"
  }'
```

#### Gợi ý sản phẩm:
```bash
curl -X GET "http://localhost:8080/api/ai/suggest-products?preference=áo sơ mi công sở"
```

#### Tư vấn size:
```bash
curl -X GET "http://localhost:8080/api/ai/size-guide?info=cao 1m75, nặng 70kg"
```

#### Health check:
```bash
curl -X GET http://localhost:8080/api/ai/health
```

## Tích Hợp Vào Hệ Thống Hiện Có

### 1. Thêm AI Chat Vào Trang Sản Phẩm

Thêm nút chat AI vào trang chi tiết sản phẩm để tư vấn:
```javascript
// Trong product details page
async function askAiAboutProduct(productName, productDescription) {
    const response = await fetch('/api/ai/chat/context', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            message: "Tư vấn cho tôi về sản phẩm này",
            context: `Sản phẩm: ${productName}. Mô tả: ${productDescription}`
        })
    });
    const data = await response.json();
    // Hiển thị data.response
}
```

### 2. Tích Hợp Vào Giỏ Hàng

Gợi ý sản phẩm bổ sung dựa trên giỏ hàng:
```javascript
async function getSuggestionsForCart(cartItems) {
    const context = "Giỏ hàng hiện tại: " + cartItems.join(", ");
    const response = await fetch('/api/ai/chat/context', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            message: "Gợi ý sản phẩm phối hợp với giỏ hàng",
            context: context
        })
    });
    return await response.json();
}
```

### 3. Tích Hợp Vào Đơn Hàng

Trả lời câu hỏi về đơn hàng:
```javascript
async function askAboutOrder(orderId, question) {
    const response = await fetch(
        `/api/ai/order-question?question=${encodeURIComponent(question)}&orderContext=Đơn hàng #${orderId}`,
        {method: 'POST'}
    );
    return await response.json();
}
```

## Tính Năng Nâng Cao

### 1. Streaming Response (Tùy chọn)

Có thể cập nhật để sử dụng streaming để response hiển thị từng phần:
```java
// Trong AiAssistantService.java
public Flux<String> chatStream(String userMessage) {
    return chatModel.stream(new Prompt(userMessage));
}
```

### 2. RAG (Retrieval-Augmented Generation)

Tích hợp với database sản phẩm để AI trả lời chính xác hơn:
- Kết nối với ProductRepository
- Tìm kiếm sản phẩm liên quan
- Thêm thông tin vào context

### 3. Chat History

Lưu lịch sử chat để AI nhớ ngữ cảnh:
```java
private Map<String, List<Message>> chatHistory = new ConcurrentHashMap<>();

public AiChatResponse chatWithHistory(String userId, String message) {
    List<Message> history = chatHistory.getOrDefault(userId, new ArrayList<>());
    history.add(new UserMessage(message));
    // ... gửi full history
}
```

## Troubleshooting

### 1. Lỗi kết nối
- Kiểm tra Llama server đang chạy: `curl http://127.0.0.1:1234/v1/models`
- Kiểm tra port 1234 không bị block bởi firewall

### 2. Response chậm
- Giảm `max-tokens` trong config
- Tăng `temperature` để response nhanh hơn (nhưng kém chính xác)

### 3. Lỗi 503 Service Unavailable
- Llama server có thể đang bận hoặc crashed
- Restart Llama server

### 4. Response không đúng ngôn ngữ
- Thêm system prompt rõ ràng hơn về việc dùng tiếng Việt
- Cập nhật SYSTEM_PROMPT trong AiAssistantService

## Performance Tips

1. **Caching**: Cache các response thường gặp
2. **Rate Limiting**: Giới hạn số request/user để tránh spam
3. **Async Processing**: Xử lý AI request không đồng bộ
4. **Load Balancing**: Nếu traffic cao, chạy nhiều Llama instances

## Security Considerations

1. **Input Validation**: Validate và sanitize user input
2. **Rate Limiting**: Đã có Bucket4j, thêm rate limit cho AI endpoints
3. **Authentication**: Yêu cầu authentication cho AI endpoints nếu cần
4. **Content Filtering**: Filter nội dung không phù hợp

## Next Steps

1. Thêm rate limiting cho AI endpoints
2. Tích hợp AI vào các trang hiện có (products, cart, orders)
3. Implement chat history
4. Thêm analytics để theo dõi AI usage
5. Fine-tune prompts để response chính xác hơn
6. Tích hợp RAG với product database

## Support

Nếu gặp vấn đề, kiểm tra logs:
```bash
tail -f logs/spring.log | grep AiAssistant
```

Hoặc enable debug logging:
```properties
logging.level.fit.iuh.edu.fashion.services.AiAssistantService=DEBUG
logging.level.org.springframework.ai=DEBUG
```

