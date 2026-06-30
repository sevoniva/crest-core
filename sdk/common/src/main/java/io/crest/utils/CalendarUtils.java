package io.crest.utils;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Calendar;

// 提供当前模块复用的工具能力
public class CalendarUtils {

    // 格式化日期时间并返回统一展示值
    public static Long getTimeAfterMonth(Long time, int months) {
        if (ObjectUtils.isEmpty(time)) time = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTimeInMillis();
    }
}
