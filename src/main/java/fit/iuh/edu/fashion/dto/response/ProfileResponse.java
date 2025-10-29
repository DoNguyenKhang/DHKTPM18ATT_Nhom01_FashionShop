package fit.iuh.edu.fashion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String gender;
    private LocalDate birthday;
    private Integer loyaltyPoint;
    private Boolean isActive;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime createdAt;
}
