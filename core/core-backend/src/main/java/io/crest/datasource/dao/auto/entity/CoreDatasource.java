package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据源实体。
 */
@TableName("core_datasource")
public class CoreDatasource implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据源主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源描述
     */
    private String description;

    /**
     * 数据源类型
     */
    private String type;

    /**
     * 父级目录编号
     */
    private Long pid;

    /**
     * 同步更新方式，0 表示替换，1 表示追加
     */
    private String editType;

    /**
     * 数据源连接配置详情
     */
    private String configuration;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 更新时间戳
     */
    private Long updateTime;

    /**
     * 最后变更人用户编号
     */
    private Long updateBy;

    /**
     * 创建人标识
     */
    private String createBy;

    /**
     * 数据源状态
     */
    private String status;

    /**
     * 调度实例编号
     */
    @TableField("scheduler_fire_instance_id")
    private String schedulerFireInstanceId;

    /**
     * 同步任务状态
     */
    private String taskStatus;

    /**
     * 是否开启数据填报
     */
    private Boolean enableDataFill;

    /**
     * 获取数据源主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据源主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据源名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置数据源名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取数据源描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置数据源描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取数据源类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据源类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取父级目录编号
     */
    public Long getPid() {
        return pid;
    }

    /**
     * 设置父级目录编号
     */
    public void setPid(Long pid) {
        this.pid = pid;
    }

    /**
     * 获取同步更新方式
     */
    public String getEditType() {
        return editType;
    }

    /**
     * 设置同步更新方式
     */
    public void setEditType(String editType) {
        this.editType = editType;
    }

    /**
     * 获取数据源连接配置详情
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * 设置数据源连接配置详情
     */
    public void setConfiguration(String configuration) {
        this.configuration = configuration;
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
     * 获取更新时间戳
     */
    public Long getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间戳
     */
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取最后变更人用户编号
     */
    public Long getUpdateBy() {
        return updateBy;
    }

    /**
     * 设置最后变更人用户编号
     */
    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    /**
     * 获取创建人标识
     */
    public String getCreateBy() {
        return createBy;
    }

    /**
     * 设置创建人标识
     */
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    /**
     * 获取数据源状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置数据源状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取调度实例编号
     */
    public String getSchedulerFireInstanceId() {
        return schedulerFireInstanceId;
    }

    /**
     * 设置调度实例编号
     */
    public void setSchedulerFireInstanceId(String schedulerFireInstanceId) {
        this.schedulerFireInstanceId = schedulerFireInstanceId;
    }

    /**
     * 获取同步任务状态
     */
    public String getTaskStatus() {
        return taskStatus;
    }

    /**
     * 设置同步任务状态
     */
    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    /**
     * 获取是否开启数据填报
     */
    public Boolean getEnableDataFill() {
        return enableDataFill;
    }

    /**
     * 设置是否开启数据填报
     */
    public void setEnableDataFill(Boolean enableDataFill) {
        this.enableDataFill = enableDataFill;
    }

    /**
     * 返回数据源实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreDatasource{" +
        "id = " + id +
        ", name = " + name +
        ", description = " + description +
        ", type = " + type +
        ", pid = " + pid +
        ", editType = " + editType +
        ", configuration = " + configuration +
        ", createTime = " + createTime +
        ", updateTime = " + updateTime +
        ", updateBy = " + updateBy +
        ", createBy = " + createBy +
        ", status = " + status +
        ", schedulerFireInstanceId = " + schedulerFireInstanceId +
        ", taskStatus = " + taskStatus +
        ", enableDataFill = " + enableDataFill +
        "}";
    }
}
