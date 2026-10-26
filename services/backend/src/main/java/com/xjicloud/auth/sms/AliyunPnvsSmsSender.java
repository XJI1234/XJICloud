package com.xjicloud.auth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Personal-developer SMS authentication; registration retains its local one-use challenge. */
@Component
@ConditionalOnProperty(prefix = "xjicloud.sms", name = "provider", havingValue = "aliyun-pnvs")
public class AliyunPnvsSmsSender implements SmsSender {
    private final SmsProperties properties;
    private final Client client;
    private final ObjectMapper json;

    @Autowired
    public AliyunPnvsSmsSender(SmsProperties properties, ObjectMapper json) throws Exception {
        this(properties, properties.isEnabled() ? new Client(new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint("dypnsapi.aliyuncs.com")
                .setProtocol("https")) : null, json);
    }

    AliyunPnvsSmsSender(SmsProperties properties, Client client, ObjectMapper json) {
        this.properties = properties;
        this.client = client;
        this.json = json;
    }

    @Override
    public void sendRegistrationCode(String phone, String code) {
        if (client == null || !properties.isEnabled()) throw unavailable();
        try {
            // Explicit code is supported by PNVS. Only our HMAC/Redis challenge verifies it.
            String parameters = json.writeValueAsString(Map.of(
                    properties.getCodeVariable(), code,
                    properties.getMinutesVariable(), Integer.toString(RegistrationSmsService.EXPIRES_SECONDS / 60)));
            var response = client.sendSmsVerifyCodeWithOptions(new SendSmsVerifyCodeRequest()
                    .setCountryCode("86")
                    .setPhoneNumber(phone)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(properties.getTemplateCode())
                    .setTemplateParam(parameters)
                    .setCodeLength(6L)
                    .setValidTime((long) RegistrationSmsService.EXPIRES_SECONDS)
                    .setInterval((long) RegistrationSmsService.RETRY_SECONDS)
                    .setReturnVerifyCode(false)
                    .setAutoRetry(0L),
                    new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false));
            if (response == null || response.getBody() == null
                    || !"OK".equals(response.getBody().getCode())
                    || !Boolean.TRUE.equals(response.getBody().getSuccess())) {
                throw unavailable();
            }
        } catch (Exception ex) {
            // SDK failures can contain phone numbers, template parameters and credential metadata.
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException("短信发送暂不可用，请稍后重试或联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
