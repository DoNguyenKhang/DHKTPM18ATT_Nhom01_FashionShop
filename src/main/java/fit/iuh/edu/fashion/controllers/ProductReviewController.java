package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.request.ProductReviewRequest;
import fit.iuh.edu.fashion.dto.response.ProductReviewResponse;
import fit.iuh.edu.fashion.security.CustomUserDetails;
import fit.iuh.edu.fashion.services.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    @GetMapping("/{productId}/reviews")
    public ResponseEntity<List<ProductReviewResponse>> getProductReviews(@PathVariable Long productId) {
        return ResponseEntity.ok(productReviewService.getProductReviews(productId));
    }

    @PostMapping("/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductReviewResponse> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody ProductReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(productReviewService.addReview(productId, request, userDetails.getId()));
    }

    @PutMapping("/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductReviewResponse> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ProductReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(productReviewService.updateReview(reviewId, request, userDetails.getId()));
    }

    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        productReviewService.deleteReview(reviewId, userDetails.getId());
        return ResponseEntity.ok().build();
    }
}

