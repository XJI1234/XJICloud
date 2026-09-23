package com.xjicloud.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.admin.dto.CloudAccessKeyDtos.CloudAccessKeyResponse;
import org.junit.jupiter.api.Test;

class CloudAccessKeyMaskTest {

    @Test
    void maskShortOrNullReturnsStars() {
        assertEquals("****", CloudAccessKeyService.maskAccessKey(null));
        assertEquals("****", CloudAccessKeyService.maskAccessKey("ab"));
        assertEquals("****", CloudAccessKeyService.maskAccessKey("abcd"));
    }

    @Test
    void maskLongKeyMatchesOssStyle() {
        assertEquals("LT****01", CloudAccessKeyService.maskAccessKey("LTAItestkey0001"));
    }

    @Test
    void responseJsonDoesNotExposeSecretField() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CloudAccessKeyResponse response = new CloudAccessKeyResponse(
                java.util.UUID.randomUUID(),
                "test",
                "LT****01",
                true,
                java.util.List.of("OSS"),
                java.util.List.of(),
                java.time.Instant.parse("2026-09-19T12:00:00Z"),
                java.time.Instant.parse("2026-09-19T12:00:00Z")
        );
        String json = mapper.writeValueAsString(response);
        assertFalse(json.contains("accessKeySecret"));
        assertFalse(json.contains("\"secret\""));
    }
}
