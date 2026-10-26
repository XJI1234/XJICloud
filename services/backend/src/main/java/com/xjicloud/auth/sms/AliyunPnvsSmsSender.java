package com.xjicloud.auth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Alibaba PNVS owns OTP generation and verification; Redis owns registration replay protection. */
@Component
public class AliyunPnvsSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(AliyunPnvsSmsSender.class);
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
    public void sendRegistrationCode(String phone, String challengeId) {
        if (client == null || !properties.isEnabled()) throw unavailable();
        try {
            String parameters = json.writeValueAsString(Map.of(
                    properties.getCodeVariable(), "##code##",
                    properties.getMinutesVariable(), Integer.toString(RegistrationSmsService.EXPIRES_SECONDS / 60)));
            var response = client.sendSmsVerifyCodeWithOptions(new SendSmsVerifyCodeRequest()
                    .setCountryCode("86")
                    .setPhoneNumber(phone)
                    .setSchemeName(properties.getSchemeName())
                    .setOutId(challengeId)
                    .setSignName(properties.getSignName())
                    .setTemplateCode(properties.getTemplateCode())
                    .setTemplateParam(parameters)
                    .setCodeLength(6L)
                    .setCodeType(1L)
                    .setDuplicatePolicy(1L)
                    .setValidTime((long) RegistrationSmsService.EXPIRES_SECONDS)
                    .setInterval((long) RegistrationSmsService.RETRY_SECONDS)
                    .setReturnVerifyCode(false)
                    .setAutoRetry(0L),
                    runtime());
            if (response == null || response.getBody() == null) throw unavailable();
            requireSuccess(response.getBody().getCode(), response.getBody().getSuccess());
        } catch (BusinessException ex) {
            throw ex;
        } catch (TeaException ex) {
            throw providerFailure(ex.getCode());
        } catch (Exception ex) {
            // SDK failures can contain phone numbers, template parameters and credential metadata.
            throw unavailable();
        }
    }

    @Override
    public boolean verifyRegistrationCode(String phone, String challengeId, String code) {
        if (client == null || !properties.isEnabled()) throw unavailable();
        try {
            var response = client.checkSmsVerifyCodeWithOptions(new CheckSmsVerifyCodeRequest()
                    .setCountryCode("86").setSchemeName(properties.getSchemeName())
                    .setPhoneNumber(phone).setOutId(challengeId).setVerifyCode(code), runtime());
            if (response == null || response.getBody() == null) throw unavailable();
            var body = response.getBody();
            if ("isv.ValidateFail".equals(body.getCode())) return false;
            requireSuccess(body.getCode(), body.getSuccess());
            if (body.getModel() == null) throw unavailable();
            // Code=OK and Success=true describe the API call, not verification success.
            return "PASS".equals(body.getModel().getVerifyResult());
        } catch (BusinessException ex) {
            throw ex;
        } catch (TeaException ex) {
            if ("isv.ValidateFail".equals(ex.getCode())) return false;
            throw providerFailure(ex.getCode());
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private static RuntimeOptions runtime() {
        return new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false);
    }

    private static void requireSuccess(String code, Boolean success) {
        if (!"OK".equals(code) || !Boolean.TRUE.equals(success)) throw providerFailure(code);
    }

    private static BusinessException providerFailure(String code) {
        // Log only a fixed allowlist; SDK messages can include credentials and personal data.
        String safeCode = switch (code == null ? "" : code) {
            case "isv.BUSINESS_LIMIT_CONTROL" -> "BUSINESS_LIMIT_CONTROL";
            case "biz.FREQUENCY" -> "FREQUENCY_FAIL";
            case "isv.MOBILE_NUMBER_ILLEGAL" -> "MOBILE_NUMBER_ILLEGAL";
            case "BUSINESS_LIMIT_CONTROL", "FREQUENCY_FAIL", "MOBILE_NUMBER_ILLEGAL",
                    "INVALID_PARAMETERS", "FUNCTION_NOT_OPENED", "Forbidden.RAM", "Forbidden.NoPermission" -> code;
            default -> "UNAVAILABLE";
        };
        log.warn("Alibaba PNVS request failed: {}", safeCode);
        return switch (safeCode) {
            case "BUSINESS_LIMIT_CONTROL", "FREQUENCY_FAIL" ->
                    new BusinessException("短信验证操作过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
            case "MOBILE_NUMBER_ILLEGAL" -> new BusinessException("手机号格式错误", HttpStatus.BAD_REQUEST);
            default -> unavailable();
        };
    }

    private static BusinessException unavailable() {
        return new BusinessException("短信认证暂不可用，请稍后重试或联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
