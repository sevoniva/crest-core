package io.crest.result;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * 统一接口响应消息体
 */
public class ResultMessage implements Serializable {

    /**
     * 响应业务状态码
     */
    private Integer code;

    /**
     * 响应提示信息
     */
    private String msg;

    /**
     * 响应数据载荷
     */
    private Object data;

    /**
     * 创建空响应消息体
     */
    public ResultMessage() {}

    /**
     * 使用状态码和提示信息创建响应消息体
     */
    public ResultMessage(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 创建无数据的成功响应
     */
    public static ResultMessage success() {
        ResultMessage result = new ResultMessage();
        result.setResultCode(ResultCode.SUCCESS);
        return result;
    }

    /**
     * 创建携带数据的成功响应
     */
    public static ResultMessage success(Object data) {
        ResultMessage result = new ResultMessage();
        result.setResultCode(ResultCode.SUCCESS);
        result.setData(data);
        return result;
    }

    /**
     * 创建失败响应
     */
    public static ResultMessage failure(ResultCode resultCode) {
        ResultMessage result = new ResultMessage();
        result.setResultCode(resultCode);
        return result;
    }

    /**
     * 创建携带数据的失败响应
     */
    public static ResultMessage failure(ResultCode resultCode, Object data) {
        ResultMessage result = new ResultMessage();
        result.setResultCode(resultCode);
        result.setData(data);
        return result;
    }

    /**
     * 将结果码枚举写入响应状态码和提示信息
     */
    public void setResultCode(ResultCode code) {
        this.code = code.code();
        this.msg = code.message();
    }
}
