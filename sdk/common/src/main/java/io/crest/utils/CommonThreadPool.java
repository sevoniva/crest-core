package io.crest.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.*;

/**
 * 通用定时线程池封装，提供普通任务、延迟任务和超时任务提交能力
 */
public class CommonThreadPool {

    /**
     * 核心线程数
     */
    private int corePoolSize = 10;

    /**
     * 最大等待队列长度阈值
     */
    private int maxQueueSize = 10;

    /**
     * 最大线程数配置
     */
    private int maximumPoolSize = 10;

    /**
     * 空闲线程保活时间
     */
    private int keepAliveSeconds = 600;

    /**
     * 实际执行任务的定时线程池
     */
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    /**
     * 初始化定时线程池
     */
    @PostConstruct
    public void init() {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(corePoolSize);
        scheduledThreadPoolExecutor.setMaximumPoolSize(corePoolSize);
        scheduledThreadPoolExecutor.setKeepAliveTime(keepAliveSeconds, TimeUnit.SECONDS);
    }

    /**
     * 容器销毁前关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        if (scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdown();
        }
    }

    /**
     * 线程池是否可用(实际队列数是否小于最大队列数)
     *
     * @return true为可用，false不可用
     */
    public boolean available() {
        return scheduledThreadPoolExecutor.getQueue().size() <= maxQueueSize;
    }

    /**
     * 添加任务，不强制限制队列数
     *
     * @param task 任务
     */
    public void addTask(Runnable task) {
        scheduledThreadPoolExecutor.execute(task);
    }

    /**
     * 添加延迟执行任务，不强制限制队列数
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  延迟时间单位
     */
    public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
        scheduledThreadPoolExecutor.schedule(task, delay, unit);
    }

    /**
     * 添加任务和超时时间（超时时间内未执行完的任务将被终止并移除线程池，防止任务执行时间过长而占用线程池）
     *
     * @param task     任务
     * @param timeOut  超时时间
     * @param timeUnit 超时时间单位
     */
    public void addTask(Runnable task, long timeOut, TimeUnit timeUnit) {
        scheduledThreadPoolExecutor.execute(() -> {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            try {
                Future future = executorService.submit(task);
                future.get(timeOut, timeUnit); // 阻塞等待任务执行完成或超时。
            } catch (TimeoutException timeoutException) {
                LogUtil.getLogger().error("timeout to execute task", timeoutException);
            } catch (Exception exception) {
                LogUtil.getLogger().error("failed to execute task", exception);
            } finally {
                if (!executorService.isShutdown()) {
                    executorService.shutdown();
                }
            }
        });
    }

    /**
     * 设置核心线程数
     */
    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    /**
     * 设置最大等待队列长度阈值
     */
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * 设置空闲线程保活时间
     */
    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }
}
