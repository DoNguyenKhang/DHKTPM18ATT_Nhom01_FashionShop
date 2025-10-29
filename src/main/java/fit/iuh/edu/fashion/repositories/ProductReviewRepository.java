package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Page<ProductReview> findByProductIdAndIsApprovedTrue(Long productId, Pageable pageable);

    List<ProductReview> findByProductIdAndIsApprovedOrderByCreatedAtDesc(Long productId, Boolean isApproved);

    @Query("SELECT AVG(pr.rating) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.isApproved = true")
    Double getAverageRatingByProductId(@Param("productId") Long productId);

    Long countByProductIdAndIsApprovedTrue(Long productId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);
}
