package com.xjicloud.auth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel;
import com.aliyun.teautil.models.RuntimeOptions;
import com.aliyun.tea.TeaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Map;

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
        return p;
    }

    @Test
    void requestsProviderGeneratedCodeWithoutReturningItOrRetrying() throws Exception {
        Client client = mock(Client.class);
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        sender.sendRegistrationCode("13800138000", "challenge-id");

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
        assertEquals("##code##", parameters.path("code").asText());
        assertEquals(1L, sent.getCodeType());
        assertEquals(1L, sent.getDuplicatePolicy());
        assertEquals("challenge-id", sent.getOutId());
        assertEquals("xjicloud-register", sent.getSchemeName());
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
                    () -> sender.sendRegistrationCode("13800138000", "challenge-id")).getStatus());
        }
        when(client.sendSmsVerifyCodeWithOptions(any(), any()))
                .thenThrow(new RuntimeException("secret-access-key phone=13800138000 code=123456"));
        var failure = assertThrows(BusinessException.class,
                () -> sender.sendRegistrationCode("13800138000", "challenge-id"));
        assertFalse(failure.getMessage().contains("secret-access-key"));
        assertFalse(failure.getMessage().contains("13800138000"));
        assertFalse(failure.getMessage().contains("challenge-id"));
        assertNull(failure.getCause());
    }

    @Test
    void disabledProviderNeverCallsApiAndTemplateVariableNamesAreConfigurable() throws Exception {
        var p = properties();
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(p, client, json);
        p.setEnabled(false);
        assertThrows(BusinessException.class, () -> sender.sendRegistrationCode("13800138000", "challenge-id"));
        verifyNoInteractions(client);
        p.setEnabled(true);
        p.setCodeVariable("verify_code");
        p.setMinutesVariable("minutes");
        p.validate();
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        sender.sendRegistrationCode("13800138000", "challenge-id");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), any());
        var parameters = json.readTree(request.getValue().getTemplateParam());
        assertEquals("##code##", parameters.path("verify_code").asText());
        assertEquals("5", parameters.path("minutes").asText());
    }

    @Test
    void invalidProviderAndTemplateConfigurationFailAtStartup() {
        var p = properties();
        p.setProvider("aliyun-sms");
        assertThrows(IllegalStateException.class, p::validate);
        p.setProvider("aliyun-pnvs");
        p.setMinutesVariable("code");
        assertThrows(IllegalStateException.class, p::validate);
        p.setMinutesVariable("min");
        p.setCodeVariable("bad\"key");
        assertThrows(IllegalStateException.class, p::validate);
        p.setCodeVariable("code");
        assertDoesNotThrow(p::validate);
        p.setAccessKeySecret("");
        assertThrows(IllegalStateException.class, p::validate);
        p.setAccessKeySecret("test-only");
        p.setSchemeName("x".repeat(21));
        assertThrows(IllegalStateException.class, p::validate);
        p.setSchemeName(" ");
        assertThrows(IllegalStateException.class, p::validate);
        p.setSchemeName("xjicloud-register");
        p.setDailyLimit(0);
        assertThrows(IllegalStateException.class, p::validate);
    }

    @Test
    void springSelectsExactlyOneSenderAndCreatesNoClientWhenDisabled() {
        var context = new ApplicationContextRunner()
                .withUserConfiguration(SmsProperties.class, AliyunPnvsSmsSender.class)
                .withBean(ObjectMapper.class, ObjectMapper::new);
        context.withPropertyValues("xjicloud.sms.enabled=false").run(c -> {
            assertNull(c.getStartupFailure());
            assertEquals(1, c.getBeansOfType(SmsSender.class).size());
            assertInstanceOf(AliyunPnvsSmsSender.class, c.getBean(SmsSender.class));
        });
        context.withPropertyValues("xjicloud.sms.provider=aliyun-pnvs", "xjicloud.sms.enabled=false").run(c -> {
            assertNull(c.getStartupFailure());
            assertEquals(1, c.getBeansOfType(SmsSender.class).size());
            assertInstanceOf(AliyunPnvsSmsSender.class, c.getBean(SmsSender.class));
            assertThrows(BusinessException.class,
                    () -> c.getBean(SmsSender.class).sendRegistrationCode("13800138000", "challenge-id"));
        });
    }

    @Test
    void mapsDocumentedProviderErrorsWithoutLeakingProviderMessages() throws Exception {
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        for (String code : new String[]{"BUSINESS_LIMIT_CONTROL", "FREQUENCY_FAIL", "MOBILE_NUMBER_ILLEGAL",
                "isv.BUSINESS_LIMIT_CONTROL", "biz.FREQUENCY", "isv.MOBILE_NUMBER_ILLEGAL",
                "INVALID_PARAMETERS", "FUNCTION_NOT_OPENED", "Forbidden.NoPermission", "UNKNOWN"}) {
            HttpStatus expected = switch (code) {
                case "BUSINESS_LIMIT_CONTROL", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL", "biz.FREQUENCY" -> HttpStatus.TOO_MANY_REQUESTS;
                case "MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_NUMBER_ILLEGAL" -> HttpStatus.BAD_REQUEST;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                    .setBody(new SendSmsVerifyCodeResponseBody().setCode(code).setSuccess(false)
                            .setMessage("secret-access-key phone=13800138000 code=123456")));
            var failure = assertThrows(BusinessException.class,
                    () -> sender.sendRegistrationCode("13800138000", "challenge-id"));
            assertEquals(expected, failure.getStatus());
            assertFalse(failure.getMessage().contains("13800138000"));
            when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenThrow(new TeaException(
                    Map.of("code", code, "message", "secret-access-key phone=13800138000 code=123456")));
            failure = assertThrows(BusinessException.class,
                    () -> sender.sendRegistrationCode("13800138000", "challenge-id"));
            assertEquals(expected, failure.getStatus());
            assertFalse(failure.getMessage().contains("secret-access-key"));
            assertNull(failure.getCause());
            when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenThrow(new TeaException(
                    Map.of("code", code, "message", "secret-access-key phone=13800138000 code=123456")));
            failure = assertThrows(BusinessException.class,
                    () -> sender.verifyRegistrationCode("13800138000", "id", "012345"));
            assertEquals(expected, failure.getStatus());
            assertFalse(failure.getMessage().contains("secret-access-key"));
            assertNull(failure.getCause());
            reset(client);
        }
    }
    @Test
    void onlyExplicitPassAuthorizesRegistrationAndCheckUsesTheSameScheme() throws Exception {
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        for (String result : new String[]{"UNKNOWN", "PASS", "pass", "", "UNEXPECTED"}) {
            when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(
                new CheckSmsVerifyCodeResponse().setBody(
                    new CheckSmsVerifyCodeResponseBody()
                        .setCode("OK").setSuccess(true).setModel(
                            new CheckSmsVerifyCodeResponseBodyModel()
                                .setVerifyResult(result))));
            assertEquals("PASS".equals(result), sender.verifyRegistrationCode("13800138000", "id", "012345"));
        }
        var request = ArgumentCaptor.forClass(CheckSmsVerifyCodeRequest.class);
        verify(client, times(5)).checkSmsVerifyCodeWithOptions(request.capture(), any());
        assertEquals("xjicloud-register", request.getValue().getSchemeName());
        assertEquals("86", request.getValue().getCountryCode());
        assertEquals("13800138000", request.getValue().getPhoneNumber());
        assertEquals("012345", request.getValue().getVerifyCode());
        assertEquals("id", request.getValue().getOutId());
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(
            new CheckSmsVerifyCodeResponse().setBody(
                new CheckSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        assertThrows(BusinessException.class, () -> sender.verifyRegistrationCode("13800138000", "id", "012345"));
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> sender.verifyRegistrationCode("13800138000", "id", "012345"));
    }

    @Test
    void documentedValidateFailMeansWrongOrExpiredCodeInsteadOfServiceFailure() throws Exception {
        Client client = mock(Client.class);
        var sender = new AliyunPnvsSmsSender(properties(), client, json);
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(new CheckSmsVerifyCodeResponse()
                .setBody(new CheckSmsVerifyCodeResponseBody().setCode("isv.ValidateFail").setSuccess(false)));
        assertFalse(sender.verifyRegistrationCode("13800138000", "id", "000000"));
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenThrow(new TeaException(
                Map.of("code", "isv.ValidateFail", "message", "phone=13800138000 code=000000")));
        assertFalse(sender.verifyRegistrationCode("13800138000", "id", "000000"));
    }

}
