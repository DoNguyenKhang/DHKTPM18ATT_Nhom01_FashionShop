package fit.iuh.edu.fashion.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @JsonBackReference("user-customer")
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column
    private LocalDate birthday;

    @Column(name = "loyalty_point", nullable = false)
    private Integer loyaltyPoint = 0;

    public enum Gender {
        MALE, FEMALE, OTHER
    }
}

