package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.Cart;
import fit.iuh.edu.fashion.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByCustomer(User customer);
    Optional<Cart> findByCustomerId(Long customerId);
}

