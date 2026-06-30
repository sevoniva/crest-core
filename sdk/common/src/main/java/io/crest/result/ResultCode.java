package io.crest.result;

/**
 * 统一结果码枚举，定义常用错误码和默认提示
 */
public enum ResultCode {
    /* 成功状态码 */
    SUCCESS(0, null),

    /* 参数错误：10001-19999 */
    PARAM_IS_INVALID(10001, "参数无效"),
    PARAM_IS_BLANK(10002, "参数为空"),
    PARAM_TYPE_BIND_ERROR(10003, "参数类型错误"),
    PARAM_NOT_COMPLETE(10004, "参数缺失"),

    /* 用户错误：20001-29999*/
    USER_NOT_LOGGED_IN(20001, "用户未登录"),
    USER_LOGIN_ERROR(20002, "账号不存在或密码错误"),
    USER_ACCOUNT_FORBIDDEN(20003, "账号已被禁用"),
    USER_NOT_EXIST(20004, "用户不存在"),
    USER_HAS_EXISTED(20005, "用户已存在"),

    /* 业务错误：30001-39999 */
    SPECIFIED_QUESTIONED_USER_NOT_EXIST(30001, "某业务出现问题"),

    /* 系统错误：40001-49999 */
    RESOURCE_NOT_FOUND(404, "资源不存在"),
    SYSTEM_INNER_ERROR(40001, "系统错误"),

    /* 数据错误：50001-599999 */
    RESULE_DATA_NONE(50001, "数据未找到"),
    DATA_IS_WRONG(50002, "数据有误"),
    DATA_ALREADY_EXISTED(50003, "数据已存在"),
    DS_RESOURCE_UNCHECKED(50004, "%s个数据集正在使用此数据源，无法删除"),

    DV_RESOURCE_UNCHECKED(50004, "%s个仪表板或数据大屏正在使用此数据集，无法删除"),

    /* 接口错误：60001-69999 */
    INTERFACE_INNER_INVOKE_ERROR(60001, "内部系统接口调用异常"),
    INTERFACE_OUTER_INVOKE_ERROR(60002, "外部系统接口调用异常"),
    INTERFACE_FORBID_VISIT(60003, "该接口禁止访问"),
    INTERFACE_ADDRESS_INVALID(60004, "接口地址无效"),
    INTERFACE_REQUEST_TIMEOUT(60005, "接口请求超时"),
    INTERFACE_EXCEED_LOAD(60006, "接口负载过高"),

    /* 权限错误：70001-79999 */
    PERMISSION_NO_ACCESS(70001, "无访问权限"),

    USER_NO_QUOTA(80001, "没有用户配额");


    /**
     * 业务结果码
     */
    private Integer code;

    /**
     * 结果码默认提示
     */
    private String message;

    /**
     * 创建结果码枚举项
     */
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 返回业务结果码
     */
    public Integer code() {
        return this.code;
    }

    /**
     * 返回结果码默认提示
     */
    public String message() {
        return this.message;
    }

    /**
     * 根据枚举名称获取默认提示
     */
    public static String getMessage(String name) {
        for (ResultCode item : ResultCode.values()) {
            if (item.name().equals(name)) {
                return item.message;
            }
        }
        return name;
    }

    /**
     * 根据枚举名称获取业务结果码
     */
    public static Integer getCode(String name) {
        for (ResultCode item : ResultCode.values()) {
            if (item.name().equals(name)) {
                return item.code;
            }
        }
        return null;
    }

    /**
     * 返回枚举名称作为字符串表示
     */
    @Override
    public String toString() {
        return this.name();
    }
}
