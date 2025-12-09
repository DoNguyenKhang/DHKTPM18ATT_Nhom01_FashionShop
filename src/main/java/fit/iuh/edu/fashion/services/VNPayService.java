package fit.iuh.edu.fashion.services;

import fit.iuh.edu.fashion.config.VNPayConfig;
import fit.iuh.edu.fashion.models.Order;
import fit.iuh.edu.fashion.models.PaymentTransaction;
import fit.iuh.edu.fashion.repositories.OrderRepository;
import fit.iuh.edu.fashion.repositories.PaymentTransactionRepository;
import fit.iuh.edu.fashion.utils.VNPayUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VNPayService {

    private static final Logger log = LoggerFactory.getLogger(VNPayService.class);

    private final VNPayConfig vnPayConfig;
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;

    public String createPaymentUrl(Order order, HttpServletRequest request) throws UnsupportedEncodingException {
        String vnp_Version = vnPayConfig.getVersion();
        String vnp_Command = vnPayConfig.getCommand();
        String vnp_TmnCode = vnPayConfig.getTmnCode();
        String vnp_Amount = order.getGrandTotal().multiply(new BigDecimal(100)).toBigIntegerExact().toString();
        String vnp_CurrCode = "VND";
        String vnp_TxnRef = order.getCode();
        String vnp_OrderInfo = "Thanh toan don hang: " + order.getCode();
        String vnp_OrderType = vnPayConfig.getOrderType();
        String vnp_Locale = "vn";
        String vnp_ReturnUrl = vnPayConfig.getReturnUrl();
        String vnp_IpAddr = VNPayUtil.getIpAddress(request);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", vnp_Amount);
        vnp_Params.put("vnp_CurrCode", vnp_CurrCode);
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", vnp_OrderType);
        vnp_Params.put("vnp_Locale", vnp_Locale);
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        List<String> queryPairs = new ArrayList<>();
        List<String> hashPairs = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue == null || fieldValue.isEmpty()) continue;
            // hashData uses unencoded key and encoded value
            hashPairs.add(fieldName + "=" + URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
            // query string encodes both
            queryPairs.add(URLEncoder.encode(fieldName, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
        }
        String hashData = String.join("&", hashPairs);
        String queryUrl = String.join("&", queryPairs);

        try {
            log.info("VNPay ReturnUrl in use: {}", vnp_ReturnUrl);
            log.info("VNPay target endpoint: {}", vnPayConfig.getVnpUrl());
        } catch (Exception ignore) {}

        String vnp_SecureHash = VNPayUtil.hmacSHA512(vnPayConfig.getHashSecret(), hashData);
        String maskedHash = (vnp_SecureHash != null && vnp_SecureHash.length() > 10)
                ? (vnp_SecureHash.substring(0, 6) + "..." + vnp_SecureHash.substring(vnp_SecureHash.length() - 4))
                : vnp_SecureHash;
        log.debug("VNPay query built (without hash): {}", queryUrl);
        log.debug("VNPay signature (masked): {}", maskedHash);

        // Append hash type for clarity/compatibility
        queryUrl += "&vnp_SecureHashType=HmacSHA512";
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        return vnPayConfig.getVnpUrl() + "?" + queryUrl;
    }

    @Transactional
    public int processCallback(Map<String, String> params) {
        String vnp_SecureHash = params.get("vnp_SecureHash");
        params.remove("vnp_SecureHashType");
        params.remove("vnp_SecureHash");

        String signValue = VNPayUtil.hashAllFields(params, vnPayConfig.getHashSecret());

        if (signValue.equals(vnp_SecureHash)) {
            String orderCode = params.get("vnp_TxnRef");
            String vnp_ResponseCode = params.get("vnp_ResponseCode");
            String transactionId = params.get("vnp_TransactionNo");

            Optional<Order> orderOpt = orderRepository.findByCode(orderCode);
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();

                // Lưu thông tin giao dịch vào PaymentTransaction
                savePaymentTransaction(order, params, "RETURN_CALLBACK");

                if ("00".equals(vnp_ResponseCode)) {
                    // Payment success
                    order.setPaymentStatus(Order.PaymentStatus.PAID);
                    order.setPaymentTransactionId(transactionId);
                    order.setPaymentTime(LocalDateTime.now());
                    order.setStatus(Order.OrderStatus.CONFIRMED);
                    orderRepository.save(order);
                    log.info("Order {} payment confirmed via callback", orderCode);
                    return 1; // Success
                } else {
                    // Payment failed
                    order.setPaymentStatus(Order.PaymentStatus.FAILED);
                    orderRepository.save(order);
                    log.warn("Order {} payment failed. Response code: {}", orderCode, vnp_ResponseCode);
                    return 0; // Failed
                }
            } else {
                return -1; // Order not found
            }
        } else {
            return -2; // Invalid signature
        }
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String> params) {
        Map<String, String> resp = new HashMap<>();
        try {
            String vnp_SecureHash = params.get("vnp_SecureHash");
            // Work on a copy to avoid mutating caller's map
            Map<String, String> fields = new HashMap<>(params);
            fields.remove("vnp_SecureHashType");
            fields.remove("vnp_SecureHash");

            String signValue = VNPayUtil.hashAllFields(fields, vnPayConfig.getHashSecret());
            if (!signValue.equals(vnp_SecureHash)) {
                resp.put("RspCode", "97");
                resp.put("Message", "Invalid Checksum");
                return resp;
            }

            String orderCode = params.get("vnp_TxnRef");
            Optional<Order> orderOpt = orderRepository.findByCode(orderCode);
            if (orderOpt.isEmpty()) {
                resp.put("RspCode", "01");
                resp.put("Message", "Order not Found");
                return resp;
            }

            Order order = orderOpt.get();

            // Validate amount: VNPay sends amount x100
            String vnpAmountStr = params.get("vnp_Amount");
            try {
                long vnpAmount = Long.parseLong(vnpAmountStr);
                long expected = order.getGrandTotal().multiply(new java.math.BigDecimal(100)).longValueExact();
                if (vnpAmount != expected) {
                    resp.put("RspCode", "04");
                    resp.put("Message", "Invalid Amount");
                    return resp;
                }
            } catch (Exception e) {
                resp.put("RspCode", "99");
                resp.put("Message", "Invalid Amount Format");
                return resp;
            }

            // Lưu thông tin giao dịch vào PaymentTransaction
            savePaymentTransaction(order, params, "IPN");

            // Idempotence: if already confirmed
            if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
                resp.put("RspCode", "02");
                resp.put("Message", "Order already confirmed");
                return resp;
            }

            String vnp_ResponseCode = params.get("vnp_ResponseCode");
            String transactionId = params.get("vnp_TransactionNo");

            if ("00".equals(vnp_ResponseCode)) {
                order.setPaymentStatus(Order.PaymentStatus.PAID);
                order.setPaymentTransactionId(transactionId);
                order.setPaymentTime(LocalDateTime.now());
                order.setStatus(Order.OrderStatus.CONFIRMED);
                orderRepository.save(order);
                log.info("Order {} payment confirmed via IPN", orderCode);
                resp.put("RspCode", "00");
                resp.put("Message", "Confirm Success");
            } else {
                order.setPaymentStatus(Order.PaymentStatus.FAILED);
                orderRepository.save(order);
                log.warn("Order {} payment failed via IPN. Response code: {}", orderCode, vnp_ResponseCode);
                resp.put("RspCode", "00");
                resp.put("Message", "Confirm Success");
            }
            return resp;
        } catch (Exception ex) {
            log.error("Error processing VNPay IPN", ex);
            resp.put("RspCode", "99");
            resp.put("Message", "Unknown error");
            return resp;
        }
    }

    /**
     * Lưu thông tin giao dịch VNPay vào database
     */
    private void savePaymentTransaction(Order order, Map<String, String> params, String transactionType) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String rawData = mapper.writeValueAsString(params);

            String vnpAmountStr = params.get("vnp_Amount");
            BigDecimal amount = new BigDecimal(vnpAmountStr).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

            String responseCode = params.get("vnp_ResponseCode");
            PaymentTransaction.TransactionStatus status;

            if ("00".equals(responseCode)) {
                status = PaymentTransaction.TransactionStatus.SUCCESS;
            } else if (responseCode != null && responseCode.startsWith("24")) {
                status = PaymentTransaction.TransactionStatus.CANCELLED;
            } else {
                status = PaymentTransaction.TransactionStatus.FAILED;
            }

            PaymentTransaction transaction = PaymentTransaction.builder()
                    .order(order)
                    .transactionId(params.get("vnp_TransactionNo"))
                    .txnRef(params.get("vnp_TxnRef"))
                    .amount(amount)
                    .paymentMethod(Order.PaymentMethod.VNPAY)
                    .status(status)
                    .responseCode(responseCode)
                    .bankCode(params.get("vnp_BankCode"))
                    .bankTranNo(params.get("vnp_BankTranNo"))
                    .cardType(params.get("vnp_CardType"))
                    .payDate(params.get("vnp_PayDate"))
                    .orderInfo(params.get("vnp_OrderInfo"))
                    .transactionType(transactionType)
                    .secureHash(params.get("vnp_SecureHash"))
                    .rawData(rawData)
                    .build();

            paymentTransactionRepository.save(transaction);
            log.info("Saved payment transaction for order: {}, type: {}, status: {}",
                    order.getCode(), transactionType, status);

            // Tạo bản ghi Payment từ PaymentTransaction
            try {
                paymentService.createPaymentFromTransaction(transaction);
            } catch (Exception e) {
                log.error("Failed to create payment record from transaction, but transaction was saved", e);
            }
        } catch (Exception e) {
            log.error("Failed to save payment transaction for order: {}", order.getCode(), e);
        }
    }
}
