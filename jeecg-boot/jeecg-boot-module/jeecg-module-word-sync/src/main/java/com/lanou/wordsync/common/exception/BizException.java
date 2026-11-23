package com.lanou.wordsync.common.exception;

/**
 * 业务异常：由 Controller/Service 主动抛出，由全局异常处理器统一转成 Result。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(500, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
