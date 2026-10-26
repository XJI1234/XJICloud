package com.xjicloud.auth.sms;

/** Returns only when the provider accepts the message. Never log phone numbers or codes. */
public interface SmsSender {
    void sendRegistrationCode(String phone, String code);
}
