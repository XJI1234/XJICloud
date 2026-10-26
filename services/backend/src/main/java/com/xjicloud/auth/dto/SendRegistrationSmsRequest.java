package com.xjicloud.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendRegistrationSmsRequest(
        @NotBlank @Pattern(regexp = "^1[3-9][0-9]{9}$", message = "请输入中国大陆11位手机号") String phone,
        @NotBlank @Size(max = 64) String captchaKey,
        @NotBlank @Size(max = 8) String captchaCode
) {}
