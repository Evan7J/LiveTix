package com.livetix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信通知服务实现（当前为模拟实现，打印日志）
 */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    @Override
    public void send(String phone, String type, Map<String, String> params) {
        if (phone == null || phone.isBlank()) {
            log.warn("SMS skipped: no phone number");
            return;
        }

        String content = buildMockContent(type, params);
        log.info("========== SMS SENT (SIMULATED) ==========");
        log.info("To: {}", maskPhone(phone));
        log.info("Type: {}", type);
        log.info("Content: {}", content);
        log.info("=============================================");
    }

    @Override
    public void sendOrderSms(String phone, String type, String orderNo, String showTitle, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("orderNo", orderNo);
        params.put("showTitle", showTitle);
        params.put("amount", amount);

        switch (type) {
            case "ORDER_CREATED" -> send(phone, type, params);
            case "PAYMENT_SUCCESS" -> send(phone, type, params);
            case "REFUND_SUCCESS" -> send(phone, type, params);
            case "REFUND_REJECTED" -> send(phone, type, params);
            default -> log.warn("Unknown SMS type: {}", type);
        }
    }

    private String buildMockContent(String type, Map<String, String> params) {
        return switch (type) {
            case "ORDER_CREATED" ->
                "【LiveTix】您的订单" + params.get("orderNo") + "已生成，" +
                "演出：" + params.get("showTitle") +
                "，请在15分钟内完成支付。";
            case "PAYMENT_SUCCESS" ->
                "【LiveTix】支付成功！订单" + params.get("orderNo") +
                "已支付 ¥" + params.get("amount") +
                "，" + params.get("showTitle") + "，祝您观演愉快！";
            case "REFUND_SUCCESS" ->
                "【LiveTix】退票成功，订单" + params.get("orderNo") +
                "已退款 ¥" + params.get("amount") + "。";
            case "REFUND_REJECTED" ->
                "【LiveTix】您的退票申请（订单" + params.get("orderNo") +
                "）未通过审核，详情请查看订单页面。";
            case "SHOW_REMINDER" ->
                "【LiveTix】您关注的「" + params.get("showTitle") +
                "」即将开售，请及时购买！";
            default -> "【LiveTix】系统通知：" + params.toString();
        };
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}