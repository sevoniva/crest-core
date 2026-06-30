package io.crest.utils;


import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

@Component
// 提供当前模块复用的工具能力
public class IDUtils {


    private static SnowFlake snowFlake;

    @Resource
    public void setSnowFlake(SnowFlake snowFlake) {
        IDUtils.snowFlake = snowFlake;
    }

    public static String randomID(Integer num) {
        num = ObjectUtils.isEmpty(num) ? 16 : num;
        return RandomStringUtils.secure().nextAlphanumeric(num);
    }

    // 业务主键默认使用雪花算法生成，避免随机字符串带来的排序和冲突风险。
    public static Long snowID() {
        return snowFlake.nextId();
    }
}
