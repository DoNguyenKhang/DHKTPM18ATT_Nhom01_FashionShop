package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByOrderId(Long orderId);

    List<PaymentTransaction> findByOrder(Order order);

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    List<PaymentTransaction> findByOrderOrderByCreatedAtDesc(Order order);

    Optional<PaymentTransaction> findFirstByOrderOrderByCreatedAtDesc(Order order);
}

