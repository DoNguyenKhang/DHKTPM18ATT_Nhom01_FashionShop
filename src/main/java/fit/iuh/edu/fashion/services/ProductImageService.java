package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.response.ProductImageResponse;
import fit.iuh.edu.fashion.models.Product;
import fit.iuh.edu.fashion.models.ProductImage;
import fit.iuh.edu.fashion.models.ProductVariant;
import fit.iuh.edu.fashion.repositories.ProductImageRepository;
import fit.iuh.edu.fashion.repositories.ProductRepository;
import fit.iuh.edu.fashion.repositories.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;

    @Value("${upload.path:src/main/resources/static/image_product}")
    private String uploadPath;

    @Transactional
    public List<ProductImageResponse> uploadImages(Long productId, MultipartFile[] files, Long variantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductVariant variant = null;
        if (variantId != null) {
            variant = productVariantRepository.findById(variantId)
                    .orElseThrow(() -> new RuntimeException("Product variant not found"));

            // Verify that the variant belongs to the product
            if (!variant.getProduct().getId().equals(productId)) {
                throw new RuntimeException("Variant does not belong to this product");
            }
        }

        // Get current max sort order
        List<ProductImage> existingImages = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
        int nextSortOrder = existingImages.isEmpty() ? 0 : existingImages.get(existingImages.size() - 1).getSortOrder() + 1;

        List<ProductImageResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            try {
                // Create upload directory if not exists
                Path uploadDir = Paths.get(uploadPath);
                if (!Files.exists(uploadDir)) {
                    Files.createDirectories(uploadDir);
                }

                // Generate unique filename
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";
                String filename = UUID.randomUUID() + extension;

                // Save file
                Path filePath = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                // Save to database
                ProductImage productImage = ProductImage.builder()
                        .product(product)
                        .variant(variant) // Will be null for product-level images
                        .url("/image_product/" + filename)
                        .altText(product.getName())
                        .sortOrder(nextSortOrder++)
                        .build();

                productImage = productImageRepository.save(productImage);
                responses.add(mapToResponse(productImage));

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload image: " + e.getMessage());
            }
        }

        return responses;
    }

    public List<ProductImageResponse> getProductImages(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
        return images.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteImage(Long id) {
        ProductImage image = productImageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        // Delete file from filesystem
        try {
            String filename = image.getUrl().substring(image.getUrl().lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadPath).resolve(filename);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Log error but continue to delete from database
            System.err.println("Failed to delete image file: " + e.getMessage());
        }

        productImageRepository.delete(image);
    }

    @Transactional
    public ProductImageResponse updateSortOrder(Long id, Integer sortOrder) {
        ProductImage image = productImageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));
        image.setSortOrder(sortOrder);
        image = productImageRepository.save(image);
        return mapToResponse(image);
    }

    private ProductImageResponse mapToResponse(ProductImage image) {
        return ProductImageResponse.builder()
                .id(image.getId())
                .url(image.getUrl())
                .altText(image.getAltText())
                .sortOrder(image.getSortOrder())
                .variantId(image.getVariant() != null ? image.getVariant().getId() : null)
                .build();
    }
}
