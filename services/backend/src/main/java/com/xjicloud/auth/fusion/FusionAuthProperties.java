package com.xjicloud.auth.fusion;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xjicloud.fusion-auth")
public class FusionAuthProperties {
    private boolean enabled;
    private String schemeCode = "";
    private String packageName = "com.xjicloud.mobile";
    private String packageSign = "";
    private String templateId = "100001";
    private int dailyLimit = 1000;

    @PostConstruct
    void validate() {
        if (dailyLimit < 1) throw new IllegalStateException("Fusion authentication daily limit must be positive");
        if (enabled && (schemeCode == null || schemeCode.isBlank() || packageName == null || packageName.isBlank()
                || packageSign == null || !packageSign.matches("[0-9a-fA-F]{32}")
                || !"100001".equals(templateId))) {
            throw new IllegalStateException("Fusion authentication requires a scheme, Android package and 32-digit hexadecimal signing MD5");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getSchemeCode() { return schemeCode; }
    public void setSchemeCode(String value) { schemeCode = value; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String value) { packageName = value; }
    public String getPackageSign() { return packageSign; }
    public void setPackageSign(String value) { packageSign = value; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String value) { templateId = value; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int value) { dailyLimit = value; }
}
