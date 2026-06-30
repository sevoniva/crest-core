package io.crest.exception;

import io.crest.result.ResultCode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
/**
 * 业务异常，统一携带错误码和前端展示消息
 */
public class CrestException extends RuntimeException {

    @Serial
    /**
     * 序列化版本号，保证异常对象跨进程传输时结构一致
     */
    private static final long serialVersionUID = 8170873998824378304L;
    /**
     * 业务错误码
     */
    private int code;

    /**
     * 业务错误消息
     */
    private String msg;

    /**
     * 使用指定错误码和消息创建业务异常
     */
    public CrestException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 使用系统内部错误码创建业务异常
     */
    private CrestException(String message) {
        this(ResultCode.SYSTEM_INNER_ERROR.code(), message);
    }

    /**
     * 根据底层异常创建系统内部错误异常
     */
    private CrestException(Throwable t) {
        super(t);
        this.code = ResultCode.SYSTEM_INNER_ERROR.code();
        this.msg = t.getMessage();
    }

    /**
     * 直接抛出系统内部错误业务异常
     */
    public static void throwException(String message) {
        throw new CrestException(message);
    }

    /**
     * 直接抛出指定错误码的业务异常
     */
    public static void throwException(int code, String message) {
        throw new CrestException(code, message);
    }

    /**
     * 创建并抛出系统内部错误业务异常
     */
    public static CrestException getException(String message) {
        throw new CrestException(message);
    }

    /**
     * 包装底层异常并抛出业务异常
     */
    public static void throwException(Throwable t) {
        throw new CrestException(t);
    }
}
