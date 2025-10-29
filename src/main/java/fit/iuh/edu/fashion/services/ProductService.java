package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.ProductRequest;
import fit.iuh.edu.fashion.dto.response.*;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository productReviewRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AuditService auditService;

    // KHÔNG cache Page objects - PageImpl không thể deserialize từ Redis
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(this::mapToProductResponse);
    }

    @Cacheable(value = "products", key = "'id_' + #id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        return mapToProductResponse(product);
    }

    @Cacheable(value = "products", key = "'slug_' + #slug")
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Product not found with slug: " + slug));
        return mapToProductResponse(product);
    }

    // KHÔNG cache Page objects
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchProducts(keyword, pageable)
                .map(this::mapToProductResponse);
    }

    // KHÔNG cache Page objects
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategory(categoryId, pageable)
                .map(this::mapToProductResponse);
    }

    // KHÔNG cache Page objects
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findByBrand(brandId, pageable)
                .map(this::mapToProductResponse);
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductResponse createProduct(ProductRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = Product.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .material(request.getMaterial())
                .origin(request.getOrigin())
                .isActive(request.getIsActive())
                .createdBy(user)
                .updatedBy(user)
                .build();

        // Set brand
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new RuntimeException("Brand not found"));
            product.setBrand(brand);
        }

        // Set categories
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            Set<Category> categories = new HashSet<>();
            for (Long categoryId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
                categories.add(category);
            }
            product.setCategories(categories);
        }

        product = productRepository.save(product);

        // Audit log
        auditService.logAction("CREATE", "Product", product.getId(), null,
                "Created product: " + product.getName());

        return mapToProductResponse(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, Long userId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Capture old values for audit
        String oldValue = String.format("Name: %s, Active: %s", product.getName(), product.getIsActive());

        product.setName(request.getName());
        product.setSlug(request.getSlug());
        product.setDescription(request.getDescription());
        product.setMaterial(request.getMaterial());
        product.setOrigin(request.getOrigin());
        product.setIsActive(request.getIsActive());
        product.setUpdatedBy(user);

        // Update brand
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new RuntimeException("Brand not found"));
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }

        // Update categories
        if (request.getCategoryIds() != null) {
            Set<Category> categories = new HashSet<>();
            for (Long categoryId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new RuntimeException("Category not found: " + categoryId));
                categories.add(category);
            }
            product.setCategories(categories);
        }

        product = productRepository.save(product);

        // Audit log
        String newValue = String.format("Name: %s, Active: %s", product.getName(), product.getIsActive());
        auditService.logAction("UPDATE", "Product", product.getId(), oldValue, newValue);

        return mapToProductResponse(product);
    }

    @CacheEvict(value = "products", allEntries = true)
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        String oldValue = "Active: " + product.getIsActive();
        product.setIsActive(false);
        productRepository.save(product);

        // Audit log
        auditService.logAction("DELETE", "Product", product.getId(), oldValue, "Active: false");
    }

    @Cacheable(value = "products", key = "'stock_' + #variantId + '_' + #quantity")
    @Transactional(readOnly = true)
    public StockCheckResponse checkStock(Long variantId, int quantity) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + variantId));

        Product product = variant.getProduct();
        String colorName = variant.getColor() != null ? variant.getColor().getName() : "N/A";
        String sizeName = variant.getSize() != null ? variant.getSize().getName() : "N/A";

        boolean isAvailable = variant.getIsActive() && variant.getStock() >= quantity;
        String message;

        if (!variant.getIsActive()) {
            message = "Sản phẩm này hiện không còn bán";
        } else if (variant.getStock() == 0) {
            message = "Sản phẩm đã hết hàng";
        } else if (variant.getStock() < quantity) {
            message = String.format("Chỉ còn %d sản phẩm trong kho. Vui lòng giảm số lượng.", variant.getStock());
        } else {
            message = "Sản phẩm có sẵn trong kho";
        }

        return StockCheckResponse.builder()
                .variantId(variantId)
                .productName(product.getName())
                .colorName(colorName)
                .sizeName(sizeName)
                .availableStock(variant.getStock())
                .isAvailable(isAvailable)
                .message(message)
                .build();
    }

    /**
     * Check and update variant status when stock reaches 0
     * Also check if all variants are out of stock to deactivate product
     */
    @Transactional
    public void checkAndUpdateStockStatus(Long variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        // If stock is 0 and variant is active, deactivate it
        if (variant.getStock() <= 0 && variant.getIsActive()) {
            variant.setIsActive(false);
            productVariantRepository.save(variant);

            // Audit log
            auditService.logAction("AUTO_UPDATE", "ProductVariant", variant.getId(),
                    "Stock: " + variant.getStock() + ", Active: true",
                    "Stock: " + variant.getStock() + ", Active: false (Auto-deactivated due to out of stock)");
        }
        // If stock > 0 and variant is inactive, you might want to reactivate (optional)
        else if (variant.getStock() > 0 && !variant.getIsActive()) {
            // Uncomment if you want auto-reactivation when stock is replenished
            // variant.setIsActive(true);
            // productVariantRepository.save(variant);
        }

        // Check if all variants of the product are out of stock
        checkAndUpdateProductStatus(variant.getProduct().getId());
    }

    /**
     * Check if all variants are out of stock and deactivate product if needed
     */
    @Transactional
    public void checkAndUpdateProductStatus(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return;
        }

        // Check if all variants are either inactive or out of stock
        boolean allVariantsUnavailable = product.getVariants().stream()
                .allMatch(v -> !v.getIsActive() || v.getStock() <= 0);

        // If all variants are unavailable and product is still active, deactivate it
        if (allVariantsUnavailable && product.getIsActive()) {
            product.setIsActive(false);
            productRepository.save(product);

            // Audit log
            auditService.logAction("AUTO_UPDATE", "Product", product.getId(),
                    "Active: true",
                    "Active: false (Auto-deactivated - all variants out of stock)");
        }
        // If at least one variant is available and product is inactive, you might want to reactivate (optional)
        else if (!allVariantsUnavailable && !product.getIsActive()) {
            // Uncomment if you want auto-reactivation when stock is replenished
            // product.setIsActive(true);
            // productRepository.save(product);
        }
    }

    private ProductResponse mapToProductResponse(Product product) {
        // Get rating statistics
        Double averageRating = productReviewRepository.getAverageRatingByProductId(product.getId());
        Long totalReviews = productReviewRepository.countByProductIdAndIsApprovedTrue(product.getId());

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .material(product.getMaterial())
                .origin(product.getOrigin())
                .isActive(product.getIsActive())
                .brand(product.getBrand() != null ? mapToBrandResponse(product.getBrand()) : null)
                .categories(product.getCategories() != null
                        ? product.getCategories().stream()
                            .map(this::mapToCategoryResponse)
                            .collect(Collectors.toList())
                        : List.of())
                .variants(product.getVariants() != null
                        ? product.getVariants().stream()
                            .map(this::mapToVariantResponse)
                            .collect(Collectors.toList())
                        : List.of())
                .images(product.getImages() != null
                        ? product.getImages().stream()
                            .map(this::mapToImageResponse)
                            .collect(Collectors.toList())
                        : List.of())
                .averageRating(averageRating != null ? averageRating : 0.0)
                .totalReviews(totalReviews != null ? totalReviews : 0L)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private BrandResponse mapToBrandResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .build();
    }

    private CategoryResponse mapToCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }

    private ProductVariantResponse mapToVariantResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .color(variant.getColor() != null ? mapToColorResponse(variant.getColor()) : null)
                .size(variant.getSize() != null ? mapToSizeResponse(variant.getSize()) : null)
                .price(variant.getPrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .stock(variant.getStock())
                .weightGram(variant.getWeightGram())
                .barcode(variant.getBarcode())
                .isActive(variant.getIsActive())
                .build();
    }

    private ColorResponse mapToColorResponse(Color color) {
        return ColorResponse.builder()
                .id(color.getId())
                .name(color.getName())
                .hex(color.getHex())
                .build();
    }

    private SizeResponse mapToSizeResponse(Size size) {
        return SizeResponse.builder()
                .id(size.getId())
                .name(size.getName())
                .note(size.getNote())
                .build();
    }

    private ProductImageResponse mapToImageResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .altText(image.getAltText())
                .sortOrder(image.getSortOrder())
                .variantId(image.getVariant() != null ? image.getVariant().getId() : null)
                .build();
    }
}
