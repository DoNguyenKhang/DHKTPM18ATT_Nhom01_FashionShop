package fit.iuh.edu.fashion.repositories;

import fit.iuh.edu.fashion.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);

    Page<Product> findByIsActive(Boolean isActive, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProducts(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.id = :categoryId AND p.isActive = true")
    Page<Product> findByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.brand.id = :brandId AND p.isActive = true")
    Page<Product> findByBrand(@Param("brandId") Long brandId, Pageable pageable);

    // Tìm kiếm nâng cao với nhiều tiêu chí
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.categories c " +
           "LEFT JOIN p.brand b " +
           "LEFT JOIN p.variants v " +
           "WHERE p.isActive = true " +
           "AND (:keyword IS NULL OR " +
           "    LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :categoryName, '%'))) " +
           "AND (:brandName IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :brandName, '%')))")
    Page<Product> searchAdvanced(
            @Param("keyword") String keyword,
            @Param("categoryName") String categoryName,
            @Param("brandName") String brandName,
            Pageable pageable
    );

    // Tìm sản phẩm theo khoảng giá
    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN p.variants v " +
           "WHERE p.isActive = true " +
           "AND v.price >= :minPrice " +
           "AND v.price <= :maxPrice")
    Page<Product> findByPriceRange(
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    // Tìm kiếm với cả keyword và khoảng giá
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.categories c " +
           "LEFT JOIN p.brand b " +
           "JOIN p.variants v " +
           "WHERE p.isActive = true " +
           "AND (:keyword IS NULL OR " +
           "    LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:minPrice IS NULL OR v.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR v.price <= :maxPrice)")
    Page<Product> searchWithPriceRange(
            @Param("keyword") String keyword,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    // Tìm kiếm toàn diện với tất cả tiêu chí
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.categories c " +
           "LEFT JOIN p.brand b " +
           "LEFT JOIN p.variants v " +
           "LEFT JOIN v.color col " +
           "LEFT JOIN v.size s " +
           "WHERE p.isActive = true " +
           "AND (:keyword IS NULL OR " +
           "    LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "    LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:categoryName IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :categoryName, '%'))) " +
           "AND (:brandName IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :brandName, '%'))) " +
           "AND (:colorName IS NULL OR LOWER(col.name) LIKE LOWER(CONCAT('%', :colorName, '%'))) " +
           "AND (:sizeName IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :sizeName, '%'))) " +
           "AND (:minPrice IS NULL OR v.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR v.price <= :maxPrice)")
    Page<Product> searchComprehensive(
            @Param("keyword") String keyword,
            @Param("categoryName") String categoryName,
            @Param("brandName") String brandName,
            @Param("colorName") String colorName,
            @Param("sizeName") String sizeName,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );
}

