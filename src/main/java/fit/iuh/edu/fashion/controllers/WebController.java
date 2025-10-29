package fit.iuh.edu.fashion.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/products")
    public String products() {
        return "products/list";
    }

    @GetMapping("/products/{slug}")
    public String productDetail() {
        return "products/detail";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/orders")
    public String orders() {
        return "orders/list";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    // Admin pages
    @GetMapping("/admin/products")
    public String adminProducts() {
        return "admin/products";
    }

    @GetMapping("/admin/orders")
    public String adminOrders() {
        return "admin/orders";
    }

    @GetMapping("/admin/users")
    public String adminUsers() {
        return "admin/users";
    }

    @GetMapping("/admin/brands")
    public String adminBrands() {
        return "admin/brands";
    }

    @GetMapping("/admin/categories")
    public String adminCategories() {
        return "admin/categories";
    }

    // Payment pages
    // NOTE: /payment/redirect is handled by RedirectController for VNPay dynamic redirect

    @GetMapping("/payment/success")
    public String paymentSuccess() {
        return "payment/success";
    }

    @GetMapping("/payment/failed")
    public String paymentFailed() {
        return "payment/failed";
    }

    @GetMapping("/payment/error")
    public String paymentError() {
        return "payment/error";
    }
}
