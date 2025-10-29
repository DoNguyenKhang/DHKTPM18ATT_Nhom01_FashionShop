package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findByVariantId(Long variantId);

    @Query("SELECT im FROM InventoryMovement im WHERE im.variant.id = :variantId " +
           "AND im.createdAt BETWEEN :startDate AND :endDate ORDER BY im.createdAt DESC")
    List<InventoryMovement> findByVariantIdAndDateRange(@Param("variantId") Long variantId,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);
}

