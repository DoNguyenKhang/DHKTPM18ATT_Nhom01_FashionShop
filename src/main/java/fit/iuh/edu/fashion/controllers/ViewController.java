package fit.iuh.edu.fashion.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/admin/coupons")
    public String adminCoupons() {
        return "admin/coupons";
    }

    @GetMapping("/admin/payments")
    public String adminPayments() {
        return "admin/payments";
    }

    @GetMapping("/admin/audit-logs")
    public String adminAuditLogs() {
        return "admin/audit-logs";
    }

    @GetMapping("/admin/system-monitor")
    public String adminSystemMonitor() {
        return "admin/system-monitor";
    }

    // Removed duplicate payment mappings - they already exist in WebController
}
