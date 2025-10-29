package fit.iuh.edu.fashion.dto.response;

import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransactionResponse {
    private Long id;
    private String orderCode;
    private String transactionId;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private String statusDescription;
    private String responseCode;
    private String responseCodeDescription;
    private String bankCode;
    private String bankTranNo;
    private String cardType;
    private String payDate;
    private String orderInfo;
    private String transactionType;
    private LocalDateTime createdAt;

    public static PaymentTransactionResponse fromEntity(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .orderCode(transaction.getOrder().getCode())
                .transactionId(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod().name())
                .status(transaction.getStatus().name())
                .statusDescription(getStatusDescription(transaction.getStatus()))
                .responseCode(transaction.getResponseCode())
                .responseCodeDescription(getResponseCodeDescription(transaction.getResponseCode()))
                .bankCode(transaction.getBankCode())
                .bankTranNo(transaction.getBankTranNo())
                .cardType(transaction.getCardType())
                .payDate(transaction.getPayDate())
                .orderInfo(transaction.getOrderInfo())
                .transactionType(transaction.getTransactionType())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private static String getStatusDescription(PaymentTransaction.TransactionStatus status) {
        switch (status) {
            case SUCCESS: return "Thành công";
            case FAILED: return "Thất bại";
            case PENDING: return "Đang xử lý";
            case CANCELLED: return "Đã hủy";
            case REFUNDED: return "Đã hoàn tiền";
            default: return "Không xác định";
        }
    }

    private static String getResponseCodeDescription(String responseCode) {
        if (responseCode == null) return null;

        switch (responseCode) {
            case "00": return "Giao dịch thành công";
            case "07": return "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới lừa đảo, giao dịch bất thường)";
            case "09": return "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking";
            case "10": return "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần";
            case "11": return "Đã hết hạn chờ thanh toán";
            case "12": return "Thẻ/Tài khoản bị khóa";
            case "13": return "Sai mật khẩu xác thực giao dịch (OTP)";
            case "24": return "Khách hàng hủy giao dịch";
            case "51": return "Tài khoản không đủ số dư";
            case "65": return "Tài khoản vượt quá hạn mức giao dịch trong ngày";
            case "75": return "Ngân hàng thanh toán đang bảo trì";
            case "79": return "Nhập sai mật khẩu thanh toán quá số lần quy định";
            default: return "Lỗi khác - Mã: " + responseCode;
        }
    }
}

