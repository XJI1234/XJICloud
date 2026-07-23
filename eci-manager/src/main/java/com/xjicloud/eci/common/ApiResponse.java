package com.xjicloud.eci.common;

/**
 * 统一 HTTP 响应体，与主仓 backend 的 ApiResponse 形态对齐：
 * {@code { "success", "message", "data" }}。
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    /** 成功且无附加文案。 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /** 成功并带提示文案。 */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /** 失败；data 恒为 null。 */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
