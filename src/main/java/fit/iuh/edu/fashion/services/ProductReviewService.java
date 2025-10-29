package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.ProductReviewRequest;
import fit.iuh.edu.fashion.dto.response.ProductReviewResponse;
import fit.iuh.edu.fashion.models.Product;
import fit.iuh.edu.fashion.models.ProductReview;
import fit.iuh.edu.fashion.models.User;
import fit.iuh.edu.fashion.repositories.ProductRepository;
import fit.iuh.edu.fashion.repositories.ProductReviewRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Cacheable(value = "productReviews", key = "'product_' + #productId")
    @Transactional(readOnly = true)
    public List<ProductReviewResponse> getProductReviews(Long productId) {
        List<ProductReview> reviews = productReviewRepository.findByProductIdAndIsApprovedOrderByCreatedAtDesc(productId, true);
        return reviews.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = {"productReviews", "products"}, allEntries = true)
    @Transactional
    public ProductReviewResponse addReview(Long productId, ProductReviewRequest request, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user already reviewed this product
        if (productReviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new RuntimeException("You have already reviewed this product");
        }

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .isApproved(true) // Auto-approve for now
                .build();

        review = productReviewRepository.save(review);
        return mapToResponse(review);
    }

    @CacheEvict(value = {"productReviews", "products"}, allEntries = true)
    @Transactional
    public ProductReviewResponse updateReview(Long reviewId, ProductReviewRequest request, Long userId) {
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        // Check if user owns this review
        if (!review.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only edit your own reviews");
        }

        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());

        review = productReviewRepository.save(review);
        return mapToResponse(review);
    }

    @CacheEvict(value = {"productReviews", "products"}, allEntries = true)
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        // Check if user owns this review or is admin
        if (!review.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only delete your own reviews");
        }

        productReviewRepository.delete(review);
    }

    private ProductReviewResponse mapToResponse(ProductReview review) {
        return ProductReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .user(ProductReviewResponse.UserBasicInfo.builder()
                        .id(review.getUser().getId())
                        .fullName(review.getUser().getFullName())
                        .username(review.getUser().getEmail())
                        .build())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
