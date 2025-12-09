package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.models.Payment;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import fit.iuh.edu.fashion.repositories.OrderRepository;
import fit.iuh.edu.fashion.repositories.PaymentRepository;
import fit.iuh.edu.fashion.repositories.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderRepository orderRepository;

    /**
     * Tạo bản ghi thanh toán từ giao dịch
     */
    @Transactional
    public Payment createPaymentFromTransaction(PaymentTransaction transaction) {
        try {
            // Kiểm tra xem đã có payment cho giao dịch này chưa
            if (transaction.getTransactionId() != null) {
                var existingPayment = paymentRepository.findByTransactionId(transaction.getTransactionId());
                if (existingPayment.isPresent()) {
                    log.info("Payment already exists for transaction: {}", transaction.getTransactionId());
                    return existingPayment.get();
                }
            }

            Payment.PaymentStatus paymentStatus;
            switch (transaction.getStatus()) {
                case SUCCESS:
                    paymentStatus = Payment.PaymentStatus.COMPLETED;
                    break;
                case FAILED:
                    paymentStatus = Payment.PaymentStatus.FAILED;
                    break;
                case REFUNDED:
                    paymentStatus = Payment.PaymentStatus.REFUNDED;
                    break;
                default:
                    paymentStatus = Payment.PaymentStatus.PENDING;
            }

            Payment payment = Payment.builder()
                    .order(transaction.getOrder())
                    .paymentMethod(transaction.getPaymentMethod().name())
                    .amount(transaction.getAmount().doubleValue())
                    .status(paymentStatus)
                    .transactionId(transaction.getTransactionId())
                    .bankCode(transaction.getBankCode())
                    .responseCode(transaction.getResponseCode())
                    .paymentInfo(transaction.getOrderInfo())
                    .build();

            if (paymentStatus == Payment.PaymentStatus.COMPLETED) {
                payment.setCompletedAt(LocalDateTime.now());
            }

            Payment savedPayment = paymentRepository.save(payment);
            log.info("Created payment record for order: {}, transaction: {}",
                    transaction.getOrder().getCode(), transaction.getTransactionId());

            return savedPayment;
        } catch (Exception e) {
            log.error("Failed to create payment from transaction: {}", transaction.getId(), e);
            throw new RuntimeException("Không thể tạo bản ghi thanh toán", e);
        }
    }

    /**
     * Tạo bản ghi thanh toán cho COD
     */
    @Transactional
    public Payment createCODPayment(Order order) {
        try {
            Payment payment = Payment.builder()
                    .order(order)
                    .paymentMethod("COD")
                    .amount(order.getGrandTotal().doubleValue())
                    .status(Payment.PaymentStatus.PENDING)
                    .paymentInfo("Thanh toán khi nhận hàng")
                    .build();

            Payment savedPayment = paymentRepository.save(payment);
            log.info("Created COD payment for order: {}", order.getCode());

            return savedPayment;
        } catch (Exception e) {
            log.error("Failed to create COD payment for order: {}", order.getCode(), e);
            throw new RuntimeException("Không thể tạo bản ghi thanh toán COD", e);
        }
    }

    /**
     * Cập nhật trạng thái thanh toán
     */
    @Transactional
    public Payment updatePaymentStatus(Long paymentId, Payment.PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy payment"));

        payment.setStatus(status);
        if (status == Payment.PaymentStatus.COMPLETED) {
            payment.setCompletedAt(LocalDateTime.now());
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Đồng bộ trạng thái với Order
        updateOrderPaymentStatus(payment.getOrder(), status);

        return savedPayment;
    }

    /**
     * Đồng bộ trạng thái thanh toán với Order
     */
    private void updateOrderPaymentStatus(Order order, Payment.PaymentStatus paymentStatus) {
        try {
            boolean updated = false;
            switch (paymentStatus) {
                case COMPLETED:
                    if (order.getPaymentStatus() != Order.PaymentStatus.PAID) {
                        order.setPaymentStatus(Order.PaymentStatus.PAID);
                        order.setPaymentTime(LocalDateTime.now());
                        updated = true;
                        log.info("Updated order {} payment status to PAID", order.getCode());
                    }
                    break;
                case FAILED:
                    if (order.getPaymentStatus() != Order.PaymentStatus.FAILED) {
                        order.setPaymentStatus(Order.PaymentStatus.FAILED);
                        updated = true;
                        log.info("Updated order {} payment status to FAILED", order.getCode());
                    }
                    break;
                case REFUNDED:
                    if (order.getPaymentStatus() != Order.PaymentStatus.REFUNDED) {
                        order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
                        updated = true;
                        log.info("Updated order {} payment status to REFUNDED", order.getCode());
                    }
                    break;
                case PENDING:
                    if (order.getPaymentStatus() != Order.PaymentStatus.UNPAID) {
                        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
                        updated = true;
                        log.info("Updated order {} payment status to UNPAID", order.getCode());
                    }
                    break;
                case CANCELLED:
                    // CANCELLED payment không update order status
                    // Vì đây là payment bị hủy khi đổi payment method
                    log.info("Payment cancelled for order {} - no order status update", order.getCode());
                    break;
            }

            // Lưu Order vào database nếu có thay đổi
            if (updated) {
                orderRepository.save(order);
            }
        } catch (Exception e) {
            log.error("Failed to update order payment status for order: {}", order.getCode(), e);
        }
    }

    /**
     * Lấy danh sách thanh toán của một đơn hàng
     */
    public List<Payment> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * Lấy tất cả thanh toán với phân trang
     */
    public Page<Payment> getAllPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return paymentRepository.findAll(pageable);
    }

    /**
     * Lấy payment theo transaction ID
     */
    public Payment getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
                .orElse(null);
    }

    /**
     * Thống kê thanh toán
     */
    public Map<String, Object> getPaymentStatistics() {
        List<Payment> allPayments = paymentRepository.findAll();

        long totalPayments = allPayments.size();
        long completedPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .count();
        long pendingPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .count();
        long failedPayments = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED)
                .count();

        double totalAmount = allPayments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .mapToDouble(Payment::getAmount)
                .sum();

        return Map.of(
                "totalPayments", totalPayments,
                "completedPayments", completedPayments,
                "pendingPayments", pendingPayments,
                "failedPayments", failedPayments,
                "totalAmount", totalAmount
        );
    }

    /**
     * Đồng bộ lại trạng thái Payment và Order cho toàn bộ dữ liệu
     * Sử dụng khi có dữ liệu cũ bị lệch giữa Payment và Order
     */
    @Transactional
    public Map<String, Object> syncAllPaymentOrderStatus() {
        log.info("Starting sync all payment-order status...");

        int totalSynced = 0;
        int totalSkipped = 0;
        int totalErrors = 0;

        List<Payment> allPayments = paymentRepository.findAll();

        for (Payment payment : allPayments) {
            try {
                Order order = payment.getOrder();
                if (order == null) {
                    log.warn("Payment {} has no associated order", payment.getId());
                    totalErrors++;
                    continue;
                }

                boolean needsSync = false;

                // Kiểm tra xem có cần đồng bộ không
                // Skip CANCELLED payments vì đã bị hủy (khi đổi payment method)
                switch (payment.getStatus()) {
                    case COMPLETED:
                        needsSync = (order.getPaymentStatus() != Order.PaymentStatus.PAID);
                        break;
                    case FAILED:
                        needsSync = (order.getPaymentStatus() != Order.PaymentStatus.FAILED);
                        break;
                    case REFUNDED:
                        needsSync = (order.getPaymentStatus() != Order.PaymentStatus.REFUNDED);
                        break;
                    case PENDING:
                        needsSync = (order.getPaymentStatus() != Order.PaymentStatus.UNPAID);
                        break;
                    case CANCELLED:
                        // Skip cancelled payments - không cần sync
                        needsSync = false;
                        break;
                }

                if (needsSync) {
                    updateOrderPaymentStatus(order, payment.getStatus());
                    totalSynced++;
                    log.info("Synced order {} with payment status {}", order.getCode(), payment.getStatus());
                } else {
                    totalSkipped++;
                }
            } catch (Exception e) {
                log.error("Failed to sync payment {}", payment.getId(), e);
                totalErrors++;
            }
        }

        log.info("Sync completed: {} synced, {} skipped, {} errors", totalSynced, totalSkipped, totalErrors);

        return Map.of(
                "totalPayments", allPayments.size(),
                "totalSynced", totalSynced,
                "totalSkipped", totalSkipped,
                "totalErrors", totalErrors,
                "message", String.format("Đã đồng bộ %d/%d payment-order", totalSynced, allPayments.size())
        );
    }
}
