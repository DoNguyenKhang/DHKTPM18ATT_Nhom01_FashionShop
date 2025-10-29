package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByProductIdOrderBySortOrder(Long productId);

    List<ProductImage> findByVariantId(Long variantId);

    void deleteByProductId(Long productId);
}
