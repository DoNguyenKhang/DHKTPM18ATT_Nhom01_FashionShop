package fit.iuh.edu.fashion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String message;
    private String context; // Optional: thêm ngữ cảnh về sản phẩm, đơn hàng, etc.
}

