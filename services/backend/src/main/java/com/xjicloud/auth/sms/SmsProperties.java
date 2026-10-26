package com.xjicloud.auth.sms;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xjicloud.sms")
public class SmsProperties {
    private boolean enabled;
    // Retained only to reject obsolete deployment settings rather than silently change products.
    private String provider = "aliyun-pnvs";
    private String schemeName = "xjicloud-register";
    private String codeVariable = "code";
    private String minutesVariable = "min";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String signName = "";
    private String templateCode = "";
    private int dailyLimit = 1000;

    @PostConstruct
    void validate() {
        if (!"aliyun-pnvs".equals(provider)) {
            throw new IllegalStateException("Only Alibaba PNVS SMS authentication is supported; remove obsolete SMS_PROVIDER");
        }
        if (!codeVariable.matches("[A-Za-z][A-Za-z0-9_]{0,31}")
                || !minutesVariable.matches("[A-Za-z][A-Za-z0-9_]{0,31}") || codeVariable.equals(minutesVariable)) {
            throw new IllegalStateException("SMS template variable names must be valid and distinct");
        }
        if (schemeName.isBlank() || schemeName.codePointCount(0, schemeName.length()) > 20) {
            throw new IllegalStateException("SMS scheme name must contain 1 to 20 characters");
        }
        if (dailyLimit < 1) throw new IllegalStateException("SMS daily limit must be positive");
        if (enabled && (accessKeyId.isBlank() || accessKeySecret.isBlank() || signName.isBlank()
                || templateCode.isBlank())) {
            throw new IllegalStateException("SMS configuration incomplete; configure credentials, sign and template");
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
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String value) { schemeName = value; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int value) { dailyLimit = value; }
}
