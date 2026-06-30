package io.crest.task.queue;

import org.apache.commons.lang3.Strings;

/**
 * Redis Streams 异常识别工具。
 */
public final class RedisStreamErrorUtils {

    private static final String BUSY_GROUP = "BUSYGROUP";

    private RedisStreamErrorUtils() {
    }

    public static boolean isBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (Strings.CI.contains(current.getMessage(), BUSY_GROUP)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
