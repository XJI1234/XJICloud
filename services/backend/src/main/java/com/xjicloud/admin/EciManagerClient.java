package com.xjicloud.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.EciManagerProperties;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class EciManagerClient {

    private final RestClient restClient;
    private final EciManagerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EciManagerClient(RestClient restClient, EciManagerProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public void putRuntimeCredential(String resource, String accessKeyId, String accessKeySecret) {
        Map<String, String> body = Map.of(
                "resource", resource,
                "accessKeyId", accessKeyId,
                "accessKeySecret", accessKeySecret
        );
        exchangePut(body, resource, accessKeyId, accessKeySecret);
    }

    public TestResult testRuntimeCredential(String resource, String accessKeyId, String accessKeySecret) {
        Map<String, String> body = Map.of(
                "resource", resource,
                "accessKeyId", accessKeyId,
                "accessKeySecret", accessKeySecret
        );
        try {
            String responseBody = restClient.post()
                    .uri("/api/v1/eci/runtime-credentials/test")
                    .header("X-Api-Key", properties.apiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseTestResult(responseBody, resource, accessKeyId, accessKeySecret);
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new BusinessException("eci-manager 不可达", HttpStatus.BAD_GATEWAY);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex, resource, accessKeyId, accessKeySecret, true);
        } catch (Exception ex) {
            throw new BusinessException("eci-manager 不可达", HttpStatus.BAD_GATEWAY);
        }
    }

    private void exchangePut(Map<String, String> body, String resource, String accessKeyId, String accessKeySecret) {
        try {
            String responseBody = restClient.put()
                    .uri("/api/v1/eci/runtime-credentials")
                    .header("X-Api-Key", properties.apiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            validateSuccessBody(responseBody, resource, accessKeyId, accessKeySecret);
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new BusinessException("eci-manager 不可达", HttpStatus.BAD_GATEWAY);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex, resource, accessKeyId, accessKeySecret, false);
        } catch (Exception ex) {
            throw new BusinessException("eci-manager 不可达", HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateSuccessBody(String responseBody, String resource, String accessKeyId, String accessKeySecret) {
        EciEnvelope envelope = parseEnvelope(responseBody);
        if (envelope != null && !envelope.success()) {
            throw new BusinessException("eci-manager 应用失败: resource=" + resource, HttpStatus.BAD_GATEWAY);
        }
    }

    private TestResult parseTestResult(String responseBody, String resource, String accessKeyId, String accessKeySecret) {
        EciEnvelope envelope = parseEnvelope(responseBody);
        if (envelope == null || !envelope.success()) {
            String message = envelope != null ? envelope.message() : null;
            throw new BusinessException(
                    message != null && !message.isBlank()
                            ? sanitize(message, accessKeyId, accessKeySecret)
                            : "eci-manager 应用失败: resource=" + resource,
                    HttpStatus.BAD_GATEWAY
            );
        }
        JsonNode data = envelope.data();
        boolean ok = data != null && data.path("ok").asBoolean(false);
        String detail = data != null ? data.path("detail").asText("") : "";
        return new TestResult(ok, sanitize(detail, accessKeyId, accessKeySecret));
    }

    private BusinessException mapResponseException(
            RestClientResponseException ex,
            String resource,
            String accessKeyId,
            String accessKeySecret,
            boolean testCall
    ) {
        int status = ex.getStatusCode().value();
        EciEnvelope envelope = parseEnvelope(ex.getResponseBodyAsString());
        String remoteMessage = envelope != null ? envelope.message() : null;
        remoteMessage = sanitize(remoteMessage, accessKeyId, accessKeySecret);

        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new BusinessException("eci-manager 拒绝 X-Api-Key", HttpStatus.BAD_GATEWAY);
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new BusinessException("eci-manager 接口不可用", HttpStatus.BAD_GATEWAY);
        }
        if (status == HttpStatus.BAD_REQUEST.value()) {
            if (remoteMessage != null && !remoteMessage.isBlank()) {
                return new BusinessException(remoteMessage, HttpStatus.BAD_REQUEST);
            }
            if (testCall) {
                return new BusinessException("eci-manager 拒绝请求: resource=" + resource, HttpStatus.BAD_REQUEST);
            }
            return new BusinessException("eci-manager 拒绝请求: resource=" + resource, HttpStatus.BAD_REQUEST);
        }
        StringBuilder message = new StringBuilder("eci-manager 应用失败: resource=").append(resource);
        if (status >= 500) {
            message.append(", status=").append(status);
        }
        if (remoteMessage != null && !remoteMessage.isBlank()) {
            message.append(", message=").append(remoteMessage);
        }
        return new BusinessException(message.toString(), HttpStatus.BAD_GATEWAY);
    }

    private EciEnvelope parseEnvelope(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, EciEnvelope.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private String sanitize(String value, String accessKeyId, String accessKeySecret) {
        if (value == null) {
            return null;
        }
        String sanitized = value;
        if (accessKeySecret != null && !accessKeySecret.isBlank() && sanitized.contains(accessKeySecret)) {
            sanitized = sanitized.replace(accessKeySecret, "***");
        }
        String apiKey = properties.apiKey();
        if (apiKey != null && !apiKey.isBlank() && sanitized.contains(apiKey)) {
            sanitized = sanitized.replace(apiKey, "***");
        }
        return sanitized;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EciEnvelope(boolean success, String message, JsonNode data) {
    }

    public record TestResult(boolean ok, String detail) {
    }
}

@Configuration
class EciManagerClientConfig {

    @Bean
    RestClient eciManagerRestClient(EciManagerProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        String baseUrl = properties.baseUrl().trim().replaceAll("/+$", "");
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean
    EciManagerClient eciManagerClient(RestClient eciManagerRestClient, EciManagerProperties properties) {
        return new EciManagerClient(eciManagerRestClient, properties);
    }
}
