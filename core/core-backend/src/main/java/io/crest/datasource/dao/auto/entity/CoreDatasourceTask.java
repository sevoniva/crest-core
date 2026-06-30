package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 数据源同步任务实体。
 */
@TableName("core_datasource_sync_task")
public class CoreDatasourceTask implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 同步任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据源编号
     */
    private Long dsId;

    /**
     * 同步任务名称
     */
    private String name;

    /**
     * 数据更新方式
     */
    private String updateType;

    /**
     * 任务开始时间戳
     */
    private Long startTime;

    /**
     * 执行频率，0 表示一次性，1 表示 Cron 调度
     */
    private String syncRate;

    /**
     * Cron 表达式
     */
    private String cron;

    /**
     * 简单重复间隔值
     */
    private Long simpleCronValue;

    /**
     * 简单重复间隔单位
     */
    private String simpleCronType;

    /**
     * 任务结束时间戳
     */
    private Long endTime;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 上次执行时间戳
     */
    private Long lastExecTime;

    /**
     * 上次执行结果状态
     */
    private String lastExecStatus;

    /**
     * 同步任务扩展数据
     */
    private String extraData;

    /**
     * 当前任务状态
     */
    private String taskStatus;

    /**
     * 获取同步任务主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置同步任务主键
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
     * 获取同步任务名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置同步任务名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取数据更新方式
     */
    public String getUpdateType() {
        return updateType;
    }

    /**
     * 设置数据更新方式
     */
    public void setUpdateType(String updateType) {
        this.updateType = updateType;
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
     * 获取执行频率
     */
    public String getSyncRate() {
        return syncRate;
    }

    /**
     * 设置执行频率
     */
    public void setSyncRate(String syncRate) {
        this.syncRate = syncRate;
    }

    /**
     * 获取 Cron 表达式
     */
    public String getCron() {
        return cron;
    }

    /**
     * 设置 Cron 表达式
     */
    public void setCron(String cron) {
        this.cron = cron;
    }

    /**
     * 获取简单重复间隔值
     */
    public Long getSimpleCronValue() {
        return simpleCronValue;
    }

    /**
     * 设置简单重复间隔值
     */
    public void setSimpleCronValue(Long simpleCronValue) {
        this.simpleCronValue = simpleCronValue;
    }

    /**
     * 获取简单重复间隔单位
     */
    public String getSimpleCronType() {
        return simpleCronType;
    }

    /**
     * 设置简单重复间隔单位
     */
    public void setSimpleCronType(String simpleCronType) {
        this.simpleCronType = simpleCronType;
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
     * 获取上次执行时间戳
     */
    public Long getLastExecTime() {
        return lastExecTime;
    }

    /**
     * 设置上次执行时间戳
     */
    public void setLastExecTime(Long lastExecTime) {
        this.lastExecTime = lastExecTime;
    }

    /**
     * 获取上次执行结果状态
     */
    public String getLastExecStatus() {
        return lastExecStatus;
    }

    /**
     * 设置上次执行结果状态
     */
    public void setLastExecStatus(String lastExecStatus) {
        this.lastExecStatus = lastExecStatus;
    }

    /**
     * 获取同步任务扩展数据
     */
    public String getExtraData() {
        return extraData;
    }

    /**
     * 设置同步任务扩展数据
     */
    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    /**
     * 获取当前任务状态
     */
    public String getTaskStatus() {
        return taskStatus;
    }

    /**
     * 设置当前任务状态
     */
    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    /**
     * 返回同步任务实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreDatasourceTask{" +
                "id = " + id +
                ", dsId = " + dsId +
                ", name = " + name +
                ", updateType = " + updateType +
                ", startTime = " + startTime +
                ", syncRate = " + syncRate +
                ", cron = " + cron +
                ", simpleCronValue = " + simpleCronValue +
                ", simpleCronType = " + simpleCronType +
                ", endTime = " + endTime +
                ", createTime = " + createTime +
                ", lastExecTime = " + lastExecTime +
                ", lastExecStatus = " + lastExecStatus +
                ", extraData = " + extraData +
                ", taskStatus = " + taskStatus +
                "}";
    }
}
