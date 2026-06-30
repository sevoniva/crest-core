package io.crest.exception;


import io.crest.i18n.Translator;
import io.crest.result.ResultCode;
import io.crest.result.ResultMessage;
import io.crest.utils.LogUtil;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一处理控制器层抛出的业务异常和系统异常
 */
@RestControllerAdvice
@SuppressWarnings("deprecation")
public class GlobalExceptionHandler {


    /**
     * 处理参数校验失败异常并返回本地化提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage MethodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        ObjectError objectError = e.getBindingResult().getAllErrors().get(0);
        String msg = objectError.getDefaultMessage();
        msg = Translator.get(msg);
        LogUtil.info(msg);
        return new ResultMessage(ResultCode.PARAM_IS_INVALID.code(), msg);
    }

    /**
     * 处理业务异常并保留业务错误码
     */
    @ExceptionHandler(CrestException.class)
    public ResultMessage crestExceptionHandler(CrestException e) {
        LogUtil.info(e.getMessage());
        return new ResultMessage(e.getCode(), e.getMessage());
    }

    /**
     * 处理客户端主动断开连接的异常
     */
    @ExceptionHandler(ClientAbortException.class)
    public void clientAbortExceptionHandler(ClientAbortException e) {
        LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
    }

    /**
     * 处理异步请求通道不可用的异常
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncRequestNotUsableExceptionHandler(AsyncRequestNotUsableException e) {
        LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
    }

    /**
     * 处理静态资源或接口资源不存在的异常
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResultMessage noResourceFoundExceptionHandler(NoResourceFoundException e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        LogUtil.debug(message);
        return new ResultMessage(ResultCode.RESOURCE_NOT_FOUND.code(), message);
    }

    /**
     * 处理请求方法不被当前接口支持的异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResultMessage methodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException e) {
        LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不支持");
    }

    /**
     * 处理请求媒体类型不被当前接口支持的异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ResultMessage mediaTypeNotSupportedExceptionHandler(HttpMediaTypeNotSupportedException e) {
        LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "请求媒体类型不支持");
    }

    /**
     * 处理响应媒体类型无法匹配客户端 Accept 的异常
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public ResultMessage mediaTypeNotAcceptableExceptionHandler(HttpMediaTypeNotAcceptableException e) {
        LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(HttpStatus.NOT_ACCEPTABLE.value(), "响应媒体类型不支持");
    }

    /**
     * 处理 JSON 结构或字段类型不匹配的请求体
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_TYPE_BIND_ERROR.code(), "请求参数格式错误");
    }

    /**
     * 处理路径或查询参数类型不匹配的请求
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage methodArgumentTypeMismatchExceptionHandler(MethodArgumentTypeMismatchException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_TYPE_BIND_ERROR.code(), "请求参数格式错误");
    }

    /**
     * 处理缺少必填查询参数、文件或请求头的请求
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            ServletRequestBindingException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage missingRequestValueExceptionHandler(Exception e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_NOT_COMPLETE.code(), "请求参数缺失");
    }

    /**
     * 处理表单或查询对象绑定失败的请求
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage bindExceptionHandler(BindException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_TYPE_BIND_ERROR.code(), "请求参数格式错误");
    }

    /**
     * 处理上传接口非 multipart 请求或 multipart 解析失败的异常
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage multipartExceptionHandler(MultipartException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_TYPE_BIND_ERROR.code(), "请求参数格式错误");
    }

    /**
     * 处理请求参数约束校验失败的请求
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultMessage constraintViolationExceptionHandler(ConstraintViolationException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_IS_INVALID.code(), "请求参数无效");
    }

    /**
     * 处理上传文件超过限制的异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ResultMessage maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
        LogUtil.info(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return new ResultMessage(ResultCode.PARAM_IS_INVALID.code(), "文件大小超过上传限制，请压缩文件或拆分后重新上传");
    }

    /**
     * 处理空指针异常中可识别的未登录场景
     */
    @ExceptionHandler(NullPointerException.class)
    public ResultMessage noUserExceptionHandler(Exception e) {
        String message = e.getMessage();
        LogUtil.error("NullPointerException: " + message, e);
        if (Strings.CS.contains(message, "Cannot invoke \"io.crest.auth.bo.TokenUserBO.getUserId()\" because \"user\" is null")) {
            return new ResultMessage(ResultCode.USER_NOT_LOGGED_IN.code(), ResultCode.USER_NOT_LOGGED_IN.message());
        }
        // 不泄露内部错误信息
        return new ResultMessage(ResultCode.PARAM_IS_BLANK.code(), "参数错误");
    }

    /**
     * 处理未被更具体处理器捕获的系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResultMessage exceptionHandler(Exception e) {
        // 记录完整错误日志
        LogUtil.error("系统内部错误: " + e.getMessage(), e);
        // 返回通用错误消息，不泄露内部信息
        return new ResultMessage(ResultCode.SYSTEM_INNER_ERROR.code(), "系统内部错误，请联系管理员");
    }

}
