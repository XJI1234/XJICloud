package com.xjicloud.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "请填写昵称") @Size(max = 128) String displayName,
        @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") @Size(max = 254) String email,
        @NotBlank(message = "请填写单位") @Size(max = 200) String organization
) {}
