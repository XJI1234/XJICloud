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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Both real SDK clients use a loopback HTTP stub; no Alibaba requests or paid SMS. */
class AliyunSmsTransportTest {
    @Test
    void bothSdkClientsSerializeAndParseWithTheSharedDependencyVersions() throws Exception {
        var captured = new AtomicReference<Map<String, String>>();
        var calls = new AtomicInteger();
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
            byte[] response = "{\"Code\":\"OK\",\"Success\":true,\"RequestId\":\"test-request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
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
            new AliyunPnvsSmsSender(p, pnvs, json).sendRegistrationCode("13800138000", "012345");
            assertEquals("13800138000", captured.get().get("PhoneNumber"));
            assertEquals("0", captured.get().get("AutoRetry"));
            assertEquals("false", captured.get().get("ReturnVerifyCode"));
            var sent = json.readTree(captured.get().get("TemplateParam"));
            assertEquals("012345", sent.path("code").asText());
            assertEquals("5", sent.path("min").asText());
            var sms = new com.aliyun.dysmsapi20170525.Client(new Config()
                    .setAccessKeyId("test-only").setAccessKeySecret("test-only")
                    .setEndpoint(endpoint).setProtocol("http"));
            new AliyunSmsSender(p, sms).sendRegistrationCode("13800138000", "012345");
            assertEquals("13800138000", captured.get().get("PhoneNumbers"));
            assertEquals("012345", json.readTree(captured.get().get("TemplateParam")).path("code").asText());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
