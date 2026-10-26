package com.xjicloud.auth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.xjicloud.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@ConditionalOnProperty(prefix = "xjicloud.sms", name = "provider", havingValue = "aliyun-sms", matchIfMissing = true)
public class AliyunSmsSender implements SmsSender {
    private final SmsProperties properties;
    private final Client client;

    @Autowired
    public AliyunSmsSender(SmsProperties properties) throws Exception {
        this(properties, properties.isEnabled() ? new Client(new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint("dysmsapi.aliyuncs.com")
                .setProtocol("https")) : null);
    }

    AliyunSmsSender(SmsProperties properties, Client client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void sendRegistrationCode(String phone, String code) {
        if (client == null) throw unavailable();
        try {
            var response = client.sendSmsWithOptions(new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(properties.getTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}"),
                    new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false));
            if (response == null || response.getBody() == null || !"OK".equals(response.getBody().getCode())) {
                throw unavailable();
            }
        } catch (Exception ex) {
            // SDK exceptions may contain request parameters and credential metadata.
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException("短信发送暂不可用，请稍后重试或联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
