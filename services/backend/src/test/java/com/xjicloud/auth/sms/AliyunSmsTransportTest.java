package com.xjicloud.auth.sms;

import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.xjicloud.common.BusinessException;
import org.springframework.http.HttpStatus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real PNVS SDK against loopback HTTP; no paid SMS. */
class AliyunSmsTransportTest {
    @Test
    void pnvsSdkSerializesSendAndCheckAndHandlesHttpErrors() throws Exception {
        var captured = new AtomicReference<Map<String, String>>();
        var calls = new AtomicInteger();
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var parameters = new HashMap<String, String>();
            for (String pair : ((query == null ? "" : query) + "&" + body).split("&")) {
                String[] field = pair.split("=", 2);
                if (field.length == 2) parameters.put(
                        URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(field[1], StandardCharsets.UTF_8));
            }
            captured.set(parameters);
            calls.incrementAndGet();
            byte[] response = (status.get() == 200
                    ? "{\"Code\":\"OK\",\"Success\":true,\"Model\":{\"VerifyResult\":\"PASS\"}}"
                    : status.get() == 401 ? "{\"Code\":\"isv.ValidateFail\",\"Success\":false}"
                    : "{\"Code\":\"FREQUENCY_FAIL\",\"Message\":\"secret phone=13800138000\",\"Success\":false}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            String endpoint = "127.0.0.1:" + server.getAddress().getPort();
            var p = new SmsProperties();
            p.setEnabled(true);
            p.setSignName("Test sign");
            p.setTemplateCode("100001");
            var json = new ObjectMapper();
            var pnvs = new com.aliyun.dypnsapi20170525.Client(new Config()
                    .setAccessKeyId("test-only").setAccessKeySecret("test-only")
                    .setEndpoint(endpoint).setProtocol("http"));
            var sender = new AliyunPnvsSmsSender(p, pnvs, json);
            sender.sendRegistrationCode("13800138000", "challenge-id");
            assertEquals("13800138000", captured.get().get("PhoneNumber"));
            assertEquals("0", captured.get().get("AutoRetry"));
            assertEquals("false", captured.get().get("ReturnVerifyCode"));
            var sent = json.readTree(captured.get().get("TemplateParam"));
            assertEquals("##code##", sent.path("code").asText());
            assertEquals("1", captured.get().get("CodeType"));
            assertEquals("1", captured.get().get("DuplicatePolicy"));
            assertEquals("6", captured.get().get("CodeLength"));
            assertEquals("300", captured.get().get("ValidTime"));
            assertEquals("5", sent.path("min").asText());
            assertTrue(sender.verifyRegistrationCode("13800138000", "challenge-id", "012345"));
            assertEquals("012345", captured.get().get("VerifyCode"));
            assertEquals("challenge-id", captured.get().get("OutId"));
            assertEquals("xjicloud-register", captured.get().get("SchemeName"));
            assertEquals("86", captured.get().get("CountryCode"));
            assertEquals(2, calls.get());
            status.set(400);
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(BusinessException.class,
                    () -> sender.sendRegistrationCode("13800138000", "id")).getStatus());
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(BusinessException.class,
                    () -> sender.verifyRegistrationCode("13800138000", "id", "012345")).getStatus());
            assertEquals(4, calls.get());
            status.set(401);
            assertFalse(sender.verifyRegistrationCode("13800138000", "id", "000000"));
            assertEquals(5, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
