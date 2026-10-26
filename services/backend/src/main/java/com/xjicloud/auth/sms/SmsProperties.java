package com.xjicloud.auth.sms;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xjicloud.sms")
public class SmsProperties {
    private boolean enabled;
    private String provider = "aliyun-sms";
    private String codeVariable = "code";
    private String minutesVariable = "min";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String signName = "";
    private String templateCode = "";
    private String codeSecret = "";
    private int dailyLimit = 1000;

    @PostConstruct
    void validate() {
        if (!"aliyun-sms".equals(provider) && !"aliyun-pnvs".equals(provider)) {
            throw new IllegalStateException("SMS provider must be aliyun-sms or aliyun-pnvs");
        }
        if ("aliyun-pnvs".equals(provider) && (!codeVariable.matches("[A-Za-z][A-Za-z0-9_]{0,31}")
                || !minutesVariable.matches("[A-Za-z][A-Za-z0-9_]{0,31}") || codeVariable.equals(minutesVariable))) {
            throw new IllegalStateException("SMS template variable names must be valid and distinct");
        }
        if (dailyLimit < 1) throw new IllegalStateException("SMS daily limit must be positive");
        if (enabled && (accessKeyId.isBlank() || accessKeySecret.isBlank() || signName.isBlank()
                || templateCode.isBlank() || codeSecret.length() < 32)) {
            throw new IllegalStateException("SMS configuration incomplete; configure credentials, sign, template and code secret");
        }
    }

    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public String getCodeVariable() { return codeVariable; }
    public void setCodeVariable(String value) { codeVariable = value; }
    public String getMinutesVariable() { return minutesVariable; }
    public void setMinutesVariable(String value) { minutesVariable = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String value) { accessKeyId = value; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String value) { accessKeySecret = value; }
    public String getSignName() { return signName; }
    public void setSignName(String value) { signName = value; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String value) { templateCode = value; }
    public String getCodeSecret() { return codeSecret; }
    public void setCodeSecret(String value) { codeSecret = value; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int value) { dailyLimit = value; }
}
