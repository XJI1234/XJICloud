package com.xjicloud.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^1[3-9][0-9]{9}$", message = "请输入中国大陆11位手机号") String phone,
        @NotBlank @Size(min = 8, max = 64, message = "密码长度须为8至64个字符") String password,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[a-f0-9-]{36}$") String smsChallengeId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{6}$", message = "请输入6位短信验证码") String smsCode
) {}
