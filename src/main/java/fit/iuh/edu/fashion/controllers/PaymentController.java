package fit.iuh.edu.fashion.controllers;

import fit.iuh.edu.fashion.dto.response.VNPayResponse;
import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.repositories.OrderRepository;
import fit.iuh.edu.fashion.services.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.UnsupportedEncodingException;
import java.util.Map;

@Controller
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final VNPayService vnPayService;
    private final OrderRepository orderRepository;

    @PostMapping("/vnpay/create")
    @ResponseBody
    public ResponseEntity<VNPayResponse> createVNPayPayment(
            @RequestParam Long orderId,
            HttpServletRequest request
    ) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            String paymentUrl = vnPayService.createPaymentUrl(order, request);

            VNPayResponse response = VNPayResponse.builder()
                    .code("00")
                    .message("success")
                    .paymentUrl(paymentUrl)
                    .orderId(order.getId())
                    .orderCode(order.getCode())
                    .build();

            return ResponseEntity.ok(response);
        } catch (UnsupportedEncodingException e) {
            VNPayResponse response = VNPayResponse.builder()
                    .code("99")
                    .message("Error creating payment URL")
                    .build();
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            VNPayResponse response = VNPayResponse.builder()
                    .code("99")
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/vnpay/callback")
    public RedirectView vnpayCallback(@RequestParam Map<String, String> params) {
        try {
            int result = vnPayService.processCallback(params);
            String orderCode = params.get("vnp_TxnRef");

            if (result == 1) {
                // Payment success
                return new RedirectView("/payment/success?orderCode=" + orderCode);
            } else if (result == 0) {
                // Payment failed
                return new RedirectView("/payment/failed?orderCode=" + orderCode);
            } else {
                // Invalid request
                return new RedirectView("/payment/error?message=Invalid+signature");
            }
        } catch (Exception e) {
            return new RedirectView("/payment/error?message=" + e.getMessage());
        }
    }

    @GetMapping("/vnpay/ipn")
    @ResponseBody
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        Map<String, String> resp = vnPayService.processIpn(params);
        return ResponseEntity.ok(resp);
    }
}
