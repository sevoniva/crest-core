package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据源同步任务日志实体。
 */
@TableName("core_datasource_sync_task_log")
public class CoreDatasourceTaskLog implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 同步任务日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据源编号
     */
    private Long dsId;

    /**
     * 同步任务编号
     */
    private Long taskId;

    /**
     * 任务开始时间戳
     */
    private Long startTime;

    /**
     * 任务结束时间戳
     */
    private Long endTime;

    /**
     * 任务执行状态
     */
    private String taskStatus;

    /**
     * 同步目标表名
     */
    private String tableName;

    /**
     * 执行日志或错误信息
     */
    private String info;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 任务触发方式
     */
    private String triggerType;

    /**
     * 获取同步任务日志主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置同步任务日志主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据源编号
     */
    public Long getDsId() {
        return dsId;
    }

    /**
     * 设置数据源编号
     */
    public void setDsId(Long dsId) {
        this.dsId = dsId;
    }

    /**
     * 获取同步任务编号
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 设置同步任务编号
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * 获取任务开始时间戳
     */
    public Long getStartTime() {
        return startTime;
    }

    /**
     * 设置任务开始时间戳
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    /**
     * 获取任务结束时间戳
     */
    public Long getEndTime() {
        return endTime;
    }

    /**
     * 设置任务结束时间戳
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    /**
     * 获取任务执行状态
     */
    public String getTaskStatus() {
        return taskStatus;
    }

    /**
     * 设置任务执行状态
     */
    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    /**
     * 获取同步目标表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置同步目标表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 获取执行日志或错误信息
     */
    public String getInfo() {
        return info;
    }

    /**
     * 设置执行日志或错误信息
     */
    public void setInfo(String info) {
        this.info = info;
    }

    /**
     * 获取创建时间戳
     */
    public Long getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间戳
     */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取任务触发方式
     */
    public String getTriggerType() {
        return triggerType;
    }

    /**
     * 设置任务触发方式
     */
    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    /**
     * 返回同步任务日志实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreDatasourceTaskLog{" +
        "id = " + id +
        ", dsId = " + dsId +
        ", taskId = " + taskId +
        ", startTime = " + startTime +
        ", endTime = " + endTime +
        ", taskStatus = " + taskStatus +
        ", tableName = " + tableName +
        ", info = " + info +
        ", createTime = " + createTime +
        ", triggerType = " + triggerType +
        "}";
    }
}
