package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SizeRequest {

    @NotBlank(message = "Size name is required")
    @Size(min = 1, max = 20, message = "Size name must be between 1 and 20 characters")
    @Pattern(regexp = "^[A-Z0-9]+$",
             message = "Size name must contain only uppercase letters and numbers (e.g., S, M, L, XL, XXL, 38, 40)")
    private String name;

    @Size(max = 100, message = "Description must not exceed 100 characters")
    private String description;

    private Boolean isActive = true;
}

