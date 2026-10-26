package com.xjicloud.auth.sms;

/** Provider generates and checks the OTP. Never return or log codes or phone numbers. */
public interface SmsSender {
    void sendRegistrationCode(String phone, String challengeId);
    boolean verifyRegistrationCode(String phone, String challengeId, String code);
}
