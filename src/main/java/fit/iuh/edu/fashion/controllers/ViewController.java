package fit.iuh.edu.fashion.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/admin/coupons")
    public String adminCoupons() {
        return "admin/coupons";
    }

    // Removed duplicate payment mappings - they already exist in WebController
}
