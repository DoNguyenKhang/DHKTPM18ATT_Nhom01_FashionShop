package fit.iuh.edu.fashion.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 160, message = "Họ tên không được vượt quá 160 ký tự")
    private String fullName;

    @Size(max = 32, message = "Số điện thoại không được vượt quá 32 ký tự")
    private String phone;

    private String address;

    @NotNull(message = "Trạng thái tài khoản không được để trống")
    private Boolean isActive;

    @NotNull(message = "Vai trò không được để trống")
    @Size(min = 1, message = "Phải chọn ít nhất một vai trò")
    private Set<String> roles;
}

