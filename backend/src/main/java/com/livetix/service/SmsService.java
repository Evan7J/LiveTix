package com.livetix.service;

import java.util.Map;

public interface SmsService {

    void send(String phone, String type, Map<String, String> params);

    void sendOrderSms(String phone, String type, String orderNo, String showTitle, String amount);
}