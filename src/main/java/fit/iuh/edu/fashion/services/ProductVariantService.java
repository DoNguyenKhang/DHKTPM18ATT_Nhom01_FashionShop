package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.ProductVariantRequest;
import fit.iuh.edu.fashion.dto.response.*;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final AuditService auditService;
    private final ProductService productService;

    @Cacheable(value = "productVariants", key = "'product_' + #productId")
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariantsByProductId(Long productId) {
        return productVariantRepository.findActiveByProductId(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "productVariants", key = "'id_' + #id")
    @Transactional(readOnly = true)
    public ProductVariantResponse getVariantById(Long id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        return mapToResponse(variant);
    }

    @CacheEvict(value = {"productVariants", "products"}, allEntries = true)
    @Transactional
    public ProductVariantResponse createVariant(ProductVariantRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(request.getSku())
                .price(request.getPrice())
                .compareAtPrice(request.getCompareAtPrice())
                .stock(request.getStock())
                .weightGram(request.getWeightGram())
                .barcode(request.getBarcode())
                .isActive(request.getIsActive())
                .build();

        if (request.getColorId() != null) {
            Color color = colorRepository.findById(request.getColorId())
                    .orElseThrow(() -> new RuntimeException("Color not found"));
            variant.setColor(color);
        }

        if (request.getSizeId() != null) {
            Size size = sizeRepository.findById(request.getSizeId())
                    .orElseThrow(() -> new RuntimeException("Size not found"));
            variant.setSize(size);
        }

        variant = productVariantRepository.save(variant);

        // Audit log
        auditService.logAction("CREATE", "ProductVariant", variant.getId(), null,
                String.format("Created variant SKU: %s, Stock: %d", variant.getSku(), variant.getStock()));

        return mapToResponse(variant);
    }

    @CacheEvict(value = {"productVariants", "products"}, allEntries = true)
    @Transactional
    public ProductVariantResponse updateVariant(Long id, ProductVariantRequest request) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant not found"));

        // Capture old values for audit
        String oldValue = String.format("SKU: %s, Price: %s, Stock: %d, Active: %s",
                variant.getSku(), variant.getPrice(), variant.getStock(), variant.getIsActive());

        int oldStock = variant.getStock();

        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setStock(request.getStock());
        variant.setWeightGram(request.getWeightGram());
        variant.setBarcode(request.getBarcode());
        variant.setIsActive(request.getIsActive());

        if (request.getColorId() != null) {
            Color color = colorRepository.findById(request.getColorId())
                    .orElseThrow(() -> new RuntimeException("Color not found"));
            variant.setColor(color);
        }

        if (request.getSizeId() != null) {
            Size size = sizeRepository.findById(request.getSizeId())
                    .orElseThrow(() -> new RuntimeException("Size not found"));
            variant.setSize(size);
        }

        variant = productVariantRepository.save(variant);

        // Check and update stock status if stock has changed
        if (oldStock != request.getStock()) {
            productService.checkAndUpdateStockStatus(variant.getId());
        }

        // Audit log
        String newValue = String.format("SKU: %s, Price: %s, Stock: %d, Active: %s",
                variant.getSku(), variant.getPrice(), variant.getStock(), variant.getIsActive());
        auditService.logAction("UPDATE", "ProductVariant", variant.getId(), oldValue, newValue);

        return mapToResponse(variant);
    }

    @CacheEvict(value = {"productVariants", "products"}, allEntries = true)
    @Transactional
    public void deleteVariant(Long id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant not found"));

        String oldValue = String.format("Active: %s, Stock: %d", variant.getIsActive(), variant.getStock());
        variant.setIsActive(false);
        productVariantRepository.save(variant);

        // Audit log
        auditService.logAction("DELETE", "ProductVariant", variant.getId(), oldValue, "Active: false");
    }

    private ProductVariantResponse mapToResponse(ProductVariant variant) {
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
}
