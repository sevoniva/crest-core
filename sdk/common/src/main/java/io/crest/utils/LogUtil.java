package io.crest.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 日志工具类，按调用方类名创建 Logger 并统一拼接调用方法信息
 */
public class LogUtil {

    /**
     * 调试级别标识
     */
    private static final String DEBUG = "DEBUG";
    /**
     * 信息级别标识
     */
    private static final String INFO = "INFO";
    /**
     * 警告级别标识
     */
    private static final String WARN = "WARN";
    /**
     * 错误级别标识
     */
    private static final String ERROR = "ERROR";

    /**
     * 初始化日志
     *
     * @return
     */
    public static Logger getLogger() {
        return LoggerFactory.getLogger(LogUtil.getLogClass());
    }

    /**
     * 根据传入级别写入日志
     */
    public static void writeLog(Object msg, String level) {
        Logger logger = LogUtil.getLogger();

        if (DEBUG.equals(level)) {
            if (logger != null && logger.isDebugEnabled()) {
                logger.debug(LogUtil.getMsg(msg));
            }
        } else if (INFO.equals(level)) {
            if (logger != null && logger.isInfoEnabled()) {
                logger.info(LogUtil.getMsg(msg));
            }
        } else if (WARN.equals(level)) {
            if (logger != null && logger.isWarnEnabled()) {
                logger.warn(LogUtil.getMsg(msg));
            }
        } else if (ERROR.equals(level)) {
            if (logger != null && logger.isErrorEnabled()) {
                logger.error(LogUtil.getMsg(msg));
            }
        } else {
            if (logger != null && logger.isErrorEnabled()) {
                logger.error("");
            }
        }
    }

    /**
     * 写入 info 日志
     */
    public static void info(Object msg) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isInfoEnabled()) {
            logger.info(LogUtil.getMsg(msg));
        }
    }

    /**
     * 写入带单个参数的 info 日志
     */
    public static void info(Object msg, Object o1) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isInfoEnabled()) {
            logger.info(LogUtil.getMsg(msg), o1);
        }
    }

    /**
     * 写入带两个参数的 info 日志
     */
    public static void info(Object msg, Object o1, Object o2) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isInfoEnabled()) {
            logger.info(LogUtil.getMsg(msg), o1, o2);
        }
    }

    /**
     * 写入带参数数组的 info 日志
     */
    public static void info(Object msg, Object[] obj) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isInfoEnabled()) {
            logger.info(LogUtil.getMsg(msg), obj);
        }
    }

    /**
     * 写入 debug 日志
     */
    public static void debug(Object msg) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isDebugEnabled()) {
            logger.debug(LogUtil.getMsg(msg));
        }
    }

    /**
     * 写入带单个参数的 debug 日志
     */
    public static void debug(Object msg, Object o) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isDebugEnabled()) {
            logger.debug(LogUtil.getMsg(msg), o);
        }
    }

    /**
     * 写入带两个参数的 debug 日志
     */
    public static void debug(Object msg, Object o1, Object o2) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isDebugEnabled()) {
            logger.debug(LogUtil.getMsg(msg), o1, o2);
        }
    }

    /**
     * 写入带参数数组的 debug 日志
     */
    public static void debug(Object msg, Object[] obj) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isDebugEnabled()) {
            logger.debug(LogUtil.getMsg(msg), obj);
        }
    }

    /**
     * 写入 warn 日志
     */
    public static void warn(Object msg) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isWarnEnabled()) {
            logger.warn(LogUtil.getMsg(msg));
        }
    }

    /**
     * 写入带单个参数的 warn 日志
     */
    public static void warn(Object msg, Object o) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isWarnEnabled()) {
            logger.warn(LogUtil.getMsg(msg), o);
        }
    }

    /**
     * 写入带两个参数的 warn 日志
     */
    public static void warn(Object msg, Object o1, Object o2) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isWarnEnabled()) {
            logger.warn(LogUtil.getMsg(msg), o1, o2);
        }
    }

    /**
     * 写入带参数数组的 warn 日志
     */
    public static void warn(Object msg, Object[] obj) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isWarnEnabled()) {
            logger.warn(LogUtil.getMsg(msg), obj);
        }
    }

    /**
     * 写入 error 日志
     */
    public static void error(Object msg) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isErrorEnabled()) {
            logger.error(LogUtil.getMsg(msg));// 并追加方法名称
        }
    }

    /**
     * 写入带单个参数的 error 日志
     */
    public static void error(Object msg, Object o) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isErrorEnabled()) {
            logger.error(LogUtil.getMsg(msg), o);
        }
    }

    /**
     * 写入带两个参数的 error 日志
     */
    public static void error(Object msg, Object o1, Object o2) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isErrorEnabled()) {
            logger.error(LogUtil.getMsg(msg), o1, o2);
        }
    }

    /**
     * 写入带参数数组的 error 日志
     */
    public static void error(Object msg, Object[] obj) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isErrorEnabled()) {
            logger.error(LogUtil.getMsg(msg), obj);
        }
    }

    /**
     * 写入带异常堆栈的 error 日志
     */
    public static void error(Object msg, Throwable ex) {
        Logger logger = LogUtil.getLogger();
        if (logger != null && logger.isErrorEnabled()) {
            logger.error(LogUtil.getMsg(msg), ex);
        }
    }

    /**
     * 拼接带调用方法名和异常信息的日志正文
     */
    public static String getMsg(Object msg, Throwable ex) {
        String str = "";

        if (msg != null) {
            str = LogUtil.getLogMethod() + "[" + msg.toString() + "]";
        } else {
            str = LogUtil.getLogMethod() + "[null]";
        }
        if (ex != null) {
            str += "[" + ex.getMessage() + "]";
        }

        return str;
    }

    /**
     * 拼接只包含调用方法名的日志正文
     */
    public static String getMsg(Object msg) {
        return LogUtil.getMsg(msg, null);
    }

    /**
     * 得到调用类名称
     *
     * @return
     */
    private static String getLogClass() {
        String str = "";

        StackTraceElement[] stack = (new Throwable()).getStackTrace();
        if (stack.length > 3) {
            StackTraceElement ste = stack[3];
            str = ste.getClassName();// 类名称
        }

        return str;
    }

    /**
     * 得到调用方法名称
     *
     * @return
     */
    private static String getLogMethod() {
        String str = "";

        StackTraceElement[] stack = (new Throwable()).getStackTrace();
        if (stack.length > 4) {
            StackTraceElement ste = stack[4];
            str = "Method[" + ste.getMethodName() + "]";// 方法名称
        }

        return str;
    }

    /**
     * 将异常堆栈转换为字符串
     */
    public static String toString(Throwable e) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            pw.flush();
        }
        return sw.toString();
    }

    /**
     * 将异常及其堆栈明细拼接为字符串
     */
    public static String getExceptionDetailsToStr(Exception e) {
        StringBuilder sb = new StringBuilder(e.toString());
        StackTraceElement[] stackElements = e.getStackTrace();
        for (StackTraceElement stackTraceElement : stackElements) {
            sb.append(stackTraceElement.toString());
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
