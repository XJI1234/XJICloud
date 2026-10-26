package com.xjicloud.auth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AliyunPnvsSmsSenderTest {
    private final ObjectMapper json = new ObjectMapper();

    private SmsProperties properties() {
        var p = new SmsProperties();
        p.setEnabled(true);
        p.setProvider("aliyun-pnvs");
        p.setAccessKeyId("test-only");
        p.setAccessKeySecret("test-only");
        p.setSignName("Test sign");
        p.setTemplateCode("100001");
        p.setCodeSecret("test-only-hmac-secret-at-least-32-characters");
        return p;
    }

    @Test
    void sendsPlatformCodeAndFiveMinuteLifetimeWithoutEitherKindOfRetry() throws Exception {
        Client client = mock(Client.class);
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        sender.sendRegistrationCode("13800138000", "012345");

        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        var options = ArgumentCaptor.forClass(RuntimeOptions.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), options.capture());
        var sent = request.getValue();
        assertEquals("13800138000", sent.getPhoneNumber());
        assertEquals("86", sent.getCountryCode());
        assertEquals("Test sign", sent.getSignName());
        assertEquals("100001", sent.getTemplateCode());
        var parameters = json.readTree(sent.getTemplateParam());
        assertEquals(2, parameters.size());
        assertEquals("012345", parameters.path("code").asText());
        assertEquals("5", parameters.path("min").asText());
        assertEquals(6L, sent.getCodeLength());
        assertEquals(300L, sent.getValidTime());
        assertEquals(60L, sent.getInterval());
        assertEquals(false, sent.getReturnVerifyCode());
        assertEquals(0L, sent.getAutoRetry());
        assertEquals(false, options.getValue().getAutoretry());
        assertEquals(3000, options.getValue().getConnectTimeout());
        assertEquals(5000, options.getValue().getReadTimeout());
    }

    @Test
    void rejectsEveryIncompleteOrFailedProviderResponseAndHidesSdkExceptions() throws Exception {
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        var responses = new SendSmsVerifyCodeResponse[] {
                null, new SendSmsVerifyCodeResponse(),
                new SendSmsVerifyCodeResponse().setBody(new SendSmsVerifyCodeResponseBody()),
                new SendSmsVerifyCodeResponse().setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(false)),
                new SendSmsVerifyCodeResponse().setBody(new SendSmsVerifyCodeResponseBody().setCode("OK")),
                new SendSmsVerifyCodeResponse().setBody(new SendSmsVerifyCodeResponseBody().setCode("FAIL").setSuccess(true))
        };
        for (var response : responses) {
            when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(response);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                    () -> sender.sendRegistrationCode("13800138000", "123456")).getStatus());
        }
        when(client.sendSmsVerifyCodeWithOptions(any(), any()))
                .thenThrow(new RuntimeException("secret-access-key phone=13800138000 code=123456"));
        var failure = assertThrows(BusinessException.class,
                () -> sender.sendRegistrationCode("13800138000", "123456"));
        assertFalse(failure.getMessage().contains("secret-access-key"));
        assertFalse(failure.getMessage().contains("13800138000"));
        assertFalse(failure.getMessage().contains("123456"));
        assertNull(failure.getCause());
    }

    @Test
    void disabledProviderNeverCallsApiAndTemplateVariableNamesAreConfigurable() throws Exception {
        var p = properties();
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(p, client, json);
        p.setEnabled(false);
        assertThrows(BusinessException.class, () -> sender.sendRegistrationCode("13800138000", "123456"));
        verifyNoInteractions(client);
        p.setEnabled(true);
        p.setCodeVariable("verify_code");
        p.setMinutesVariable("minutes");
        p.validate();
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        sender.sendRegistrationCode("13800138000", "123456");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), any());
        var parameters = json.readTree(request.getValue().getTemplateParam());
        assertEquals("123456", parameters.path("verify_code").asText());
        assertEquals("5", parameters.path("minutes").asText());
    }

    @Test
    void invalidProviderAndTemplateConfigurationFailAtStartup() {
        var p = properties();
        p.setProvider("pnvs-typo");
        assertThrows(IllegalStateException.class, p::validate);
        p.setProvider("aliyun-pnvs");
        p.setMinutesVariable("code");
        assertThrows(IllegalStateException.class, p::validate);
        p.setMinutesVariable("min");
        p.setCodeVariable("bad\"key");
        assertThrows(IllegalStateException.class, p::validate);
        p.setCodeVariable("code");
        assertDoesNotThrow(p::validate);
    }

    @Test
    void springSelectsExactlyOneSenderAndCreatesNoClientWhenDisabled() {
        var context = new ApplicationContextRunner()
                .withUserConfiguration(SmsProperties.class, AliyunSmsSender.class, AliyunPnvsSmsSender.class)
                .withBean(ObjectMapper.class, ObjectMapper::new);
        context.withPropertyValues("xjicloud.sms.provider=aliyun-sms", "xjicloud.sms.enabled=false").run(c -> {
            assertNull(c.getStartupFailure());
            assertEquals(1, c.getBeansOfType(SmsSender.class).size());
            assertInstanceOf(AliyunSmsSender.class, c.getBean(SmsSender.class));
        });
        context.withPropertyValues("xjicloud.sms.provider=aliyun-pnvs", "xjicloud.sms.enabled=false").run(c -> {
            assertNull(c.getStartupFailure());
            assertEquals(1, c.getBeansOfType(SmsSender.class).size());
            assertInstanceOf(AliyunPnvsSmsSender.class, c.getBean(SmsSender.class));
            assertThrows(BusinessException.class,
                    () -> c.getBean(SmsSender.class).sendRegistrationCode("13800138000", "123456"));
        });
    }
}
