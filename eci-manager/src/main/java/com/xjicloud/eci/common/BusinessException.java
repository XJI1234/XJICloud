package com.xjicloud.eci.common;

import org.springframework.http.HttpStatus;

/**
 * 可预期的业务异常，携带 HTTP 状态码，由 {@link GlobalExceptionHandler} 转成统一失败响应。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    /** 默认 400 Bad Request。 */
    public BusinessException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
