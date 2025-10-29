package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.AiChatRequest;
import fit.iuh.edu.fashion.dto.AiChatResponse;
import fit.iuh.edu.fashion.services.AiAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    /**
     * Endpoint chat đơn giản
     * POST /api/ai/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody String message) {
        log.info("Received chat request: {}", message);
        AiChatResponse response = aiAssistantService.chat(message);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint chat với ngữ cảnh sản phẩm
     * POST /api/ai/chat/product
     */
    @PostMapping("/chat/product")
    public ResponseEntity<AiChatResponse> chatProduct(@RequestBody String message) {
        log.info("Received product chat request: {}", message);
        AiChatResponse response = aiAssistantService.chatWithProductContext(message);
        return ResponseEntity.ok(response);
    }

    /**
     * Tìm kiếm và tư vấn sản phẩm
     * GET /api/ai/search?keyword=...&limit=10
     */
    @GetMapping("/search")
    public ResponseEntity<AiChatResponse> searchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Search request: keyword={}, limit={}", keyword, limit);
        AiChatResponse response = aiAssistantService.searchAndAdvise(keyword, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Tư vấn theo thương hiệu
     * GET /api/ai/brand/{brandId}?question=...&limit=10
     */
    @GetMapping("/brand/{brandId}")
    public ResponseEntity<AiChatResponse> consultBrand(
            @PathVariable Long brandId,
            @RequestParam String question,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Brand consultation: brandId={}, question={}", brandId, question);
        AiChatResponse response = aiAssistantService.consultByBrand(brandId, question, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Tư vấn theo danh mục
     * GET /api/ai/category/{categoryId}?question=...&limit=10
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<AiChatResponse> consultCategory(
            @PathVariable Long categoryId,
            @RequestParam String question,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Category consultation: categoryId={}, question={}", categoryId, question);
        AiChatResponse response = aiAssistantService.consultByCategory(categoryId, question, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check cho AI service
     * GET /api/ai/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        try {
            aiAssistantService.chat("Hello");
            return ResponseEntity.ok("AI service is running");
        } catch (Exception e) {
            log.error("AI service health check failed", e);
            return ResponseEntity.status(503).body("AI service is unavailable");
        }
    }

    /**
     * Test direct LM Studio - Bypass all caching and timeouts
     * GET /api/ai/test-direct?message=...
     */
    @GetMapping("/test-direct")
    public ResponseEntity<String> testDirect(@RequestParam(defaultValue = "Hello") String message) {
        log.info("Direct test to LM Studio: {}", message);
        long startTime = System.currentTimeMillis();
        
        try {
            AiChatResponse response = aiAssistantService.chatWithContext(
                new AiChatRequest(message, null)
            );
            
            long duration = System.currentTimeMillis() - startTime;
            
            return ResponseEntity.ok(String.format(
                "Response in %d ms:\n%s", 
                duration, 
                response.getResponse()
            ));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Direct test failed after {} ms", duration, e);
            return ResponseEntity.status(500).body(
                String.format("Failed after %d ms: %s", duration, e.getMessage())
            );
        }
    }
}
