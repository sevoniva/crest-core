package io.crest.utils;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 提供当前模块复用的工具能力
public class DateUtils {

    // 格式化日期时间并返回统一展示值
    public static String time2String(Long time, String pattern) {
        if (StringUtils.isBlank(pattern)) pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime timeByMilli = Instant.ofEpochMilli(time).atZone(zoneId).toLocalDateTime();
        return format.format(timeByMilli);
    }
    // 格式化日期时间并返回统一展示值
    public static String time2String(Long time) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        return time2String(time, pattern);
    }
}
