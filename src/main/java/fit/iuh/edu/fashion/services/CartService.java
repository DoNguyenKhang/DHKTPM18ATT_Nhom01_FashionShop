package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.dto.request.CartItemRequest;
import fit.iuh.edu.fashion.dto.request.OrderItemRequest;
import fit.iuh.edu.fashion.dto.response.CartItemResponse;
import fit.iuh.edu.fashion.dto.response.CartResponse;
import fit.iuh.edu.fashion.dto.response.ProductVariantResponse;
import fit.iuh.edu.fashion.models.*;
import fit.iuh.edu.fashion.repositories.CartItemRepository;
import fit.iuh.edu.fashion.repositories.CartRepository;
import fit.iuh.edu.fashion.repositories.ProductImageRepository;
import fit.iuh.edu.fashion.repositories.ProductVariantRepository;
import fit.iuh.edu.fashion.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final ProductVariantService productVariantService;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElseGet(() -> createNewCart(user));

        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse addToCart(Long userId, CartItemRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElseGet(() -> createNewCart(user));

        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        // Check stock
        if (variant.getStock() < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock");
        }

        // Check if item already in cart
        CartItem existingItem = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElse(null);

        if (existingItem != null) {
            // Update quantity
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (variant.getStock() < newQuantity) {
                throw new RuntimeException("Insufficient stock");
            }
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
        } else {
            // Add new item
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(cartItem);
            cartItemRepository.save(cartItem);
        }

        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse updateCartItem(Long userId, Long itemId, Integer quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to this cart");
        }

        // Check stock
        if (cartItem.getVariant().getStock() < quantity) {
            throw new RuntimeException("Insufficient stock");
        }

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);

        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse removeCartItem(Long userId, Long itemId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to this cart");
        }

        cart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);

        return mapToCartResponse(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.getItems().clear();
        cartRepository.save(cart);
    }

    @Transactional(readOnly = true)
    public List<OrderItemRequest> getCartItemsForOrder(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByCustomer(user)
                .orElse(null);

        if (cart == null || cart.getItems().isEmpty()) {
            return new ArrayList<>();
        }

        return cart.getItems().stream()
                .map(item -> new OrderItemRequest(item.getVariant().getId(), item.getQuantity()))
                .collect(Collectors.toList());
    }

    private Cart createNewCart(User user) {
        Cart cart = Cart.builder()
                .customer(user)
                .build();
        return cartRepository.save(cart);
    }

    private CartResponse mapToCartResponse(Cart cart) {
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = subtotal.subtract(discountTotal);

        return CartResponse.builder()
                .id(cart.getId())
                .items(cart.getItems().stream()
                        .map(this::mapToCartItemResponse)
                        .collect(Collectors.toList()))
                .subtotal(subtotal)
                .discountTotal(discountTotal)
                .grandTotal(grandTotal)
                .totalItems(totalItems)
                .build();
    }

    private CartItemResponse mapToCartItemResponse(CartItem item) {
        ProductVariantResponse variantResponse = productVariantService.getVariantById(item.getVariant().getId());

        BigDecimal lineTotal = item.getVariant().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        // Get first image URL - check variant images first, then product images
        String imageUrl = null;

        // Try to get variant-specific image
        List<ProductImage> variantImages = productImageRepository.findByVariantId(item.getVariant().getId());
        if (variantImages != null && !variantImages.isEmpty()) {
            imageUrl = variantImages.get(0).getUrl();
        } else {
            // Fallback to product images
            List<ProductImage> productImages = productImageRepository.findByProductIdOrderBySortOrder(item.getVariant().getProduct().getId());
            if (productImages != null && !productImages.isEmpty()) {
                imageUrl = productImages.get(0).getUrl();
            }
        }

        // Get current stock and check availability
        Integer availableStock = item.getVariant().getStock();
        Boolean outOfStock = availableStock <= 0;
        Boolean insufficientStock = item.getQuantity() > availableStock;

        return CartItemResponse.builder()
                .id(item.getId())
                .variant(variantResponse)
                .productName(item.getVariant().getProduct().getName())
                .colorName(item.getVariant().getColor() != null ? item.getVariant().getColor().getName() : null)
                .sizeName(item.getVariant().getSize() != null ? item.getVariant().getSize().getName() : null)
                .imageUrl(imageUrl)
                .quantity(item.getQuantity())
                .unitPrice(item.getVariant().getPrice())
                .lineTotal(lineTotal)
                .availableStock(availableStock)
                .outOfStock(outOfStock)
                .insufficientStock(insufficientStock)
                .build();
    }
}
