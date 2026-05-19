package com.lightbot.common;

import com.lightbot.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过错误码枚举构造异常
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 通过错误码枚举构造异常（支持格式化消息）
     *
     * @param errorCode 错误码枚举
     * @param args      格式化参数
     */
    public BizException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.code = errorCode.getCode();
    }
}
