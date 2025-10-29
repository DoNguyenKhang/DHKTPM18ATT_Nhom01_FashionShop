package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.response.ProductImageResponse;
import fit.iuh.edu.fashion.services.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/product-images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;

    @PostMapping(value = "/upload/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<List<ProductImageResponse>> uploadProductImages(
            @PathVariable Long productId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) Long variantId
    ) {
        List<ProductImageResponse> responses = productImageService.uploadImages(productId, files, variantId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ProductImageResponse>> getProductImages(@PathVariable Long productId) {
        return ResponseEntity.ok(productImageService.getProductImages(productId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        productImageService.deleteImage(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/sort-order")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF_PRODUCT')")
    public ResponseEntity<ProductImageResponse> updateSortOrder(
            @PathVariable Long id,
            @RequestParam Integer sortOrder
    ) {
        return ResponseEntity.ok(productImageService.updateSortOrder(id, sortOrder));
    }
}

