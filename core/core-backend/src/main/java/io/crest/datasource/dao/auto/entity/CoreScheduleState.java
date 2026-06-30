package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 调度器状态实体。
 */
@TableName("core_schedule_scheduler_state")
public class CoreScheduleState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId("SCHED_NAME")
    private String schedName;

    @TableField("INSTANCE_NAME")
    private String instanceName;

    @TableField("LAST_CHECKIN_TIME")
    private Long lastCheckinTime;

    @TableField("CHECKIN_INTERVAL")
    private Long checkinInterval;

    /**
     * 获取调度器名称
     */
    public String getSchedName() {
        return schedName;
    }

    /**
     * 设置调度器名称
     */
    public void setSchedName(String schedName) {
        this.schedName = schedName;
    }

    /**
     * 获取调度器实例名称
     */
    public String getInstanceName() {
        return instanceName;
    }

    /**
     * 设置调度器实例名称
     */
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    /**
     * 获取最后一次心跳时间
     */
    public Long getLastCheckinTime() {
        return lastCheckinTime;
    }

    /**
     * 设置最后一次心跳时间
     */
    public void setLastCheckinTime(Long lastCheckinTime) {
        this.lastCheckinTime = lastCheckinTime;
    }

    /**
     * 获取心跳间隔
     */
    public Long getCheckinInterval() {
        return checkinInterval;
    }

    /**
     * 设置心跳间隔
     */
    public void setCheckinInterval(Long checkinInterval) {
        this.checkinInterval = checkinInterval;
    }

    /**
     * 返回调度器状态的调试字符串
     */
    @Override
    public String toString() {
        return "CoreScheduleState{" +
        "schedName = " + schedName +
        ", instanceName = " + instanceName +
        ", lastCheckinTime = " + lastCheckinTime +
        ", checkinInterval = " + checkinInterval +
        "}";
    }
}
