package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.CatalogDataDTO;
import fit.iuh.edu.fashion.dto.ProductCatalogDTO;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service để load và cache catalog data cho AI
 * Giảm thiểu query database, tối ưu hiệu suất
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CatalogCacheService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;

    /**
     * Lấy toàn bộ catalog data - CACHED 5 phút
     */
    @Cacheable(value = "catalogData", unless = "#result == null")
    @Transactional(readOnly = true)
    public CatalogDataDTO getCatalogData() {
        log.info("Loading catalog data from database...");

        long totalProducts = productRepository.count();
        long activeProducts = productRepository.findByIsActive(true, PageRequest.of(0, Integer.MAX_VALUE)).getTotalElements();

        List<CatalogDataDTO.BrandInfo> brands = brandRepository.findAll().stream()
            .map(b -> {
                long count = productRepository.findByBrand(b.getId(), PageRequest.of(0, 1)).getTotalElements();

                return CatalogDataDTO.BrandInfo.builder()
                    .id(b.getId())
                    .name(b.getName())
                    .description(b.getDescription())
                    .productCount(count)
                    .build();
            })
            .collect(Collectors.toList());

        List<CatalogDataDTO.CategoryInfo> categories = categoryRepository.findAll().stream()
            .map(c -> {
                long count = productRepository.findByCategory(c.getId(), PageRequest.of(0, 1)).getTotalElements();

                return CatalogDataDTO.CategoryInfo.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .description(c.getDescription())
                    .parentId(c.getParent() != null ? c.getParent().getId() : null)
                    .parentName(c.getParent() != null ? c.getParent().getName() : null)
                    .productCount(count)
                    .build();
            })
            .collect(Collectors.toList());

        List<CatalogDataDTO.ColorInfo> colors = colorRepository.findAll().stream()
            .map(c -> {
                long count = productRepository.findAll().stream()
                    .filter(p -> p.getVariants().stream()
                        .anyMatch(v -> v.getColor() != null && v.getColor().getId().equals(c.getId())
                            && v.getIsActive() && v.getStock() > 0))
                    .count();

                return CatalogDataDTO.ColorInfo.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .hex(c.getHex())
                    .productCount(count)
                    .build();
            })
            .collect(Collectors.toList());

        List<CatalogDataDTO.SizeInfo> sizes = sizeRepository.findAll().stream()
            .map(s -> {
                long count = productRepository.findAll().stream()
                    .filter(p -> p.getVariants().stream()
                        .anyMatch(v -> v.getSize() != null && v.getSize().getId().equals(s.getId())
                            && v.getIsActive() && v.getStock() > 0))
                    .count();

                return CatalogDataDTO.SizeInfo.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .note(s.getNote())
                    .productCount(count)
                    .build();
            })
            .collect(Collectors.toList());

        log.info("Catalog data loaded: {} brands, {} categories, {} colors, {} sizes",
            brands.size(), categories.size(), colors.size(), sizes.size());

        return CatalogDataDTO.builder()
            .brands(brands)
            .categories(categories)
            .colors(colors)
            .sizes(sizes)
            .totalProducts(totalProducts)
            .activeProducts(activeProducts)
            .build();
    }

    /**
     * Lấy top products - CACHED 2 phút - SỬ DỤNG REPOSITORY METHOD
     */
    @Cacheable(value = "topProducts", key = "#limit", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<ProductCatalogDTO> getTopProducts(int limit) {
        log.info("Loading top {} products from database...", limit);

        List<Product> products = productRepository.findByIsActive(true, PageRequest.of(0, limit)).getContent();

        log.info("Found {} active products", products.size());

        return products.stream()
            .map(this::convertToProductCatalogDTO)
            .collect(Collectors.toList());
    }

    /**
     * Search products by keyword - SỬ DỤNG REPOSITORY SEARCH METHOD
     */
    @Cacheable(value = "productSearch", key = "#keyword + '_' + #limit", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<ProductCatalogDTO> searchProducts(String keyword, int limit) {
        log.info("Searching products with keyword: '{}', limit: {}", keyword, limit);

        // SỬ DỤNG repository method đã có sẵn thay vì findAll() và filter
        List<Product> products = productRepository.searchProducts(keyword, PageRequest.of(0, limit)).getContent();

        log.info("Found {} products matching keyword '{}'", products.size(), keyword);

        if (!products.isEmpty()) {
            log.info("Sample products found:");
            products.stream().limit(3).forEach(p ->
                log.info("  - {} (ID: {})", p.getName(), p.getId())
            );
        } else {
            log.warn("No products found for keyword: {}", keyword);
        }

        return products.stream()
            .map(this::convertToProductCatalogDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get products by brand - SỬ DỤNG REPOSITORY METHOD
     */
    @Cacheable(value = "productsByBrand", key = "#brandId + '_' + #limit", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<ProductCatalogDTO> getProductsByBrand(Long brandId, int limit) {
        log.info("Loading products for brand ID: {}", brandId);

        List<Product> products = productRepository.findByBrand(brandId, PageRequest.of(0, limit)).getContent();

        log.info("Found {} products for brand {}", products.size(), brandId);

        return products.stream()
            .map(this::convertToProductCatalogDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get products by category - SỬ DỤNG REPOSITORY METHOD
     */
    @Cacheable(value = "productsByCategory", key = "#categoryId + '_' + #limit", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<ProductCatalogDTO> getProductsByCategory(Long categoryId, int limit) {
        log.info("Loading products for category ID: {}", categoryId);

        List<Product> products = productRepository.findByCategory(categoryId, PageRequest.of(0, limit)).getContent();

        log.info("Found {} products for category {}", products.size(), categoryId);

        return products.stream()
            .map(this::convertToProductCatalogDTO)
            .collect(Collectors.toList());
    }

    /**
     * Convert Product entity to DTO (tối ưu cho AI)
     */
    private ProductCatalogDTO convertToProductCatalogDTO(Product product) {
        log.debug("Converting product: {} (ID: {})", product.getName(), product.getId());

        List<ProductCatalogDTO.VariantInfo> variants = product.getVariants().stream()
            .map(v -> ProductCatalogDTO.VariantInfo.builder()
                .color(v.getColor() != null ? v.getColor().getName() : null)
                .size(v.getSize() != null ? v.getSize().getName() : null)
                .price(v.getPrice())
                .stock(v.getStock())
                .available(v.getIsActive() && v.getStock() > 0)
                .build())
            .collect(Collectors.toList());

        BigDecimal minPrice = variants.stream()
            .map(ProductCatalogDTO.VariantInfo::getPrice)
            .min(BigDecimal::compareTo)
            .orElse(null);

        BigDecimal maxPrice = variants.stream()
            .map(ProductCatalogDTO.VariantInfo::getPrice)
            .max(BigDecimal::compareTo)
            .orElse(null);

        List<String> categoryNames = product.getCategories().stream()
            .map(Category::getName)
            .collect(Collectors.toList());

        ProductCatalogDTO dto = ProductCatalogDTO.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
            .categories(categoryNames)
            .variants(variants)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .material(product.getMaterial())
            .origin(product.getOrigin())
            .build();

        log.debug("Converted DTO: {} - Price: {}", dto.getName(), dto.getMinPrice());

        return dto;
    }
}
