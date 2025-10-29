package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9]).*$",
             message = "Password must contain at least one letter and one number")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 160, message = "Full name must be between 2 and 160 characters")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Full name must contain only letters and spaces")
    private String fullName;

    @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Invalid phone number format")
    private String phone;
}
