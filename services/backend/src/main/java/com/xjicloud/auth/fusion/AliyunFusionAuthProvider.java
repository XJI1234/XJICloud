package com.xjicloud.auth.fusion;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.GetFusionAuthTokenRequest;
import com.aliyun.dypnsapi20170525.models.VerifyWithFusionAuthTokenRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.xjicloud.auth.sms.SmsProperties;
import com.xjicloud.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AliyunFusionAuthProvider implements FusionAuthProvider {
    private final FusionAuthProperties properties;
    private final Client client;

    @Autowired
    public AliyunFusionAuthProvider(FusionAuthProperties properties, SmsProperties sms) {
        this(properties, createClient(properties, sms));
    }

    AliyunFusionAuthProvider(FusionAuthProperties properties, Client client) {
        this.properties = properties;
        this.client = client;
    }

    private static Client createClient(FusionAuthProperties properties, SmsProperties sms) {
        properties.validate();
        if (!properties.isEnabled()) return null;
        Config config = sharedConfig(sms);
        try {
            return new Client(config);
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    /** Fusion shares the existing RAM identity and does not depend on SMS OTP being enabled. */
    static Config sharedConfig(SmsProperties sms) {
        if (sms.getAccessKeyId() == null || sms.getAccessKeyId().isBlank()
                || sms.getAccessKeySecret() == null || sms.getAccessKeySecret().isBlank()) {
            throw new IllegalStateException("Fusion authentication requires the shared SMS RAM credentials");
        }
        return new Config().setAccessKeyId(sms.getAccessKeyId()).setAccessKeySecret(sms.getAccessKeySecret())
                .setEndpoint("dypnsapi.aliyuncs.com").setProtocol("https");
    }

    @Override
    public String issueAuthToken() {
        requireEnabled();
        try {
            var response = client.getFusionAuthTokenWithOptions(new GetFusionAuthTokenRequest()
                    .setSchemeCode(properties.getSchemeCode())
                    .setPlatform("Android")
                    .setPackageName(properties.getPackageName())
                    .setPackageSign(properties.getPackageSign())
                    .setDurationSeconds(900L), runtime());
            if (response == null || response.getBody() == null) throw unavailable();
            var body = response.getBody();
            requireSuccess(body.getCode(), body.getSuccess());
            if (body.getModel() == null || body.getModel().isBlank()) throw unavailable();
            return body.getModel();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // Provider messages and SDK causes may contain credentials, tokens or phone numbers.
            throw unavailable();
        }
    }

    @Override
    public String verifyPhone(String verifyToken) {
        requireEnabled();
        if (verifyToken == null || verifyToken.isBlank()) throw verificationFailed();
        try {
            var response = client.verifyWithFusionAuthTokenWithOptions(new VerifyWithFusionAuthTokenRequest()
                    .setVerifyToken(verifyToken), runtime());
            if (response == null || response.getBody() == null) throw unavailable();
            var body = response.getBody();
            requireSuccess(body.getCode(), body.getSuccess());
            var model = body.getModel();
            if (model == null || !"PASS".equals(model.getVerifyResult())
                    || model.getPhoneNumber() == null || !model.getPhoneNumber().matches("1[3-9][0-9]{9}")) {
                throw verificationFailed();
            }
            return model.getPhoneNumber();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || client == null) throw unavailable();
    }

    private static RuntimeOptions runtime() {
        return new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false);
    }

    private static void requireSuccess(String code, Boolean success) {
        if (!"OK".equals(code) || !Boolean.TRUE.equals(success)) throw unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException("融合认证暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static BusinessException verificationFailed() {
        return new BusinessException("号码认证未通过，请重新验证", HttpStatus.UNAUTHORIZED);
    }
}
