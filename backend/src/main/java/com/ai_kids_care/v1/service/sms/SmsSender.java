package com.ai_kids_care.v1.service.sms;

public interface SmsSender {
    SmsSendResult sendSms(String to, String text, String title);
}
