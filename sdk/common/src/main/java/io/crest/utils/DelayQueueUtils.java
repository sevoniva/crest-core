package io.crest.utils;

import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 提供当前模块复用的工具能力
public class DelayQueueUtils {

    private static final List<String> delayQueueList = new ArrayList<>();

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    // 执行当前业务请求并写入处理结果
    public static void execute(String key, Runnable runnable, Long seconds) {
        seconds = ObjectUtils.isEmpty(seconds) ? 5L : seconds;
        if (delayQueueList.contains(key)) return;
        delayQueueList.add(key);
        executorService.schedule(() -> {
            runnable.run();
            delayQueueList.remove(key);
        }, seconds, TimeUnit.SECONDS);
    }
}
