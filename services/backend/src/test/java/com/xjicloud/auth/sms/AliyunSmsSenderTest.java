package com.xjicloud.auth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.xjicloud.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AliyunSmsSenderTest {
    private SmsProperties properties() {
        var p = new SmsProperties();
        p.setEnabled(true);
        p.setAccessKeyId("test-only");
        p.setAccessKeySecret("test-only");
        p.setSignName("Test sign");
        p.setTemplateCode("SMS_TEST");
        p.setCodeSecret("test-only-hmac-secret-at-least-32-characters");
        return p;
    }

    @Test
    void checksProviderResponseAndBuildsSingleRecipientTemplateWithoutRetry() throws Exception {
        Client client = mock(Client.class);
        when(client.sendSmsWithOptions(any(), any()))
                .thenReturn(new SendSmsResponse().setBody(new SendSmsResponseBody().setCode("OK")));
        var sender = new AliyunSmsSender(properties(), client);
        sender.sendRegistrationCode("13800138000", "123456");
        var request = ArgumentCaptor.forClass(SendSmsRequest.class);
        var options = ArgumentCaptor.forClass(RuntimeOptions.class);
        verify(client).sendSmsWithOptions(request.capture(), options.capture());
        assertEquals("13800138000", request.getValue().getPhoneNumbers());
        assertEquals("Test sign", request.getValue().getSignName());
        assertEquals("SMS_TEST", request.getValue().getTemplateCode());
        assertEquals("{\"code\":\"123456\"}", request.getValue().getTemplateParam());
        assertEquals(false, options.getValue().getAutoretry());
        assertEquals(3000, options.getValue().getConnectTimeout());
        assertEquals(5000, options.getValue().getReadTimeout());
    }

    @Test
    void providerBusinessFailureAndExceptionNeverLeakResponseDetails() throws Exception {
        Client client = mock(Client.class);
        var sender = new AliyunSmsSender(properties(), client);
        when(client.sendSmsWithOptions(any(), any())).thenReturn(new SendSmsResponse()
                .setBody(new SendSmsResponseBody().setCode("isv.BUSINESS_LIMIT_CONTROL")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> sender.sendRegistrationCode("13800138000", "123456")).getStatus());
        when(client.sendSmsWithOptions(any(), any())).thenThrow(new RuntimeException("secret-access-key and phone and otp"));
        var failure = assertThrows(BusinessException.class, () -> sender.sendRegistrationCode("13800138000", "123456"));
        assertFalse(failure.getMessage().contains("secret-access-key"));
        assertNull(failure.getCause());
    }

    @Test
    void enabledConfigurationRequiresCredentialsAndStrongCodeSecret() {
        var p = new SmsProperties();
        p.setEnabled(true);
        assertThrows(IllegalStateException.class, p::validate);
        assertDoesNotThrow(properties()::validate);
        assertDoesNotThrow(new SmsProperties()::validate);
    }
}
