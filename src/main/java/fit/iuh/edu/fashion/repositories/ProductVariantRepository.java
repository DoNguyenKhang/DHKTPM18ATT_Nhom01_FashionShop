package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    Optional<ProductVariant> findBySku(String sku);

    List<ProductVariant> findByProductId(Long productId);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.product.id = :productId AND pv.isActive = true")
    List<ProductVariant> findActiveByProductId(@Param("productId") Long productId);

    @Query("SELECT pv FROM ProductVariant pv WHERE pv.isActive = true AND pv.stock > 0")
    List<ProductVariant> findInStock();

    /**
     * Lock variant for update to prevent race condition
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdWithLock(@Param("id") Long id);

    /**
     * Atomic decrease stock - returns number of rows affected
     * Only decreases if sufficient stock is available
     */
    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.stock = pv.stock - :quantity, pv.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE pv.id = :id AND pv.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * Atomic increase stock when order is cancelled or refunded
     */
    @Modifying
    @Query("UPDATE ProductVariant pv SET pv.stock = pv.stock + :quantity, pv.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE pv.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}

