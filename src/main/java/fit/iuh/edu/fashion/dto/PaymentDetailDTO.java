package fit.iuh.edu.fashion.dto;

import fit.iuh.edu.fashion.models.Payment;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailDTO {

    // Payment info
    private Long id;
    private String paymentMethod;
    private Double amount;
    private String status;
    private String transactionId;
    private String bankCode;
    private String responseCode;
    private String paymentInfo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // Order info (minimal)
    private OrderBasicDTO order;

    // Transaction info (optional)
    private TransactionBasicDTO transaction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBasicDTO {
        private Long id;
        private String code;
        private Double grandTotal;
        private String status;
        private String shipLine1;
        private String shipCity;
        private CustomerBasicDTO customer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerBasicDTO {
        private Long id;
        private String fullName;
        private String email;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionBasicDTO {
        private Long id;
        private String transactionId;
        private String bankTranNo;
        private String cardType;
        private String payDate;
        private String transactionType;
        private String status;
    }

    public static PaymentDetailDTO fromPayment(Payment payment, PaymentTransaction transaction) {
        PaymentDetailDTOBuilder builder = PaymentDetailDTO.builder()
                .id(payment.getId())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .bankCode(payment.getBankCode())
                .responseCode(payment.getResponseCode())
                .paymentInfo(payment.getPaymentInfo())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt());

        // Add order info if available
        if (payment.getOrder() != null) {
            var order = payment.getOrder();

            CustomerBasicDTO customerDTO = null;
            if (order.getCustomer() != null) {
                customerDTO = CustomerBasicDTO.builder()
                        .id(order.getCustomer().getId())
                        .fullName(order.getCustomer().getFullName())
                        .email(order.getCustomer().getEmail())
                        .phone(order.getCustomer().getPhone())
                        .build();
            }

            OrderBasicDTO orderDTO = OrderBasicDTO.builder()
                    .id(order.getId())
                    .code(order.getCode())
                    .grandTotal(order.getGrandTotal().doubleValue())
                    .status(order.getStatus().name())
                    .shipLine1(order.getShipLine1())
                    .shipCity(order.getShipCity())
                    .customer(customerDTO)
                    .build();

            builder.order(orderDTO);
        }

        // Add transaction info if available
        if (transaction != null) {
            TransactionBasicDTO transactionDTO = TransactionBasicDTO.builder()
                    .id(transaction.getId())
                    .transactionId(transaction.getTransactionId())
                    .bankTranNo(transaction.getBankTranNo())
                    .cardType(transaction.getCardType())
                    .payDate(transaction.getPayDate())
                    .transactionType(transaction.getTransactionType())
                    .status(transaction.getStatus().name())
                    .build();

            builder.transaction(transactionDTO);
        }

        return builder.build();
    }
}

