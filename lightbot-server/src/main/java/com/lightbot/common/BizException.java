package com.lightbot.common;

import com.lightbot.enums.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    private final HttpStatus httpStatus;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    /**
     * 通过错误码枚举构造异常
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 通过错误码枚造异常（支持格式化消息）
     */
    public BizException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 通过错误码枚举构造异常（保留原始异常）
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }
}
