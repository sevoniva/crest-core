package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据引擎实体。
 */
@TableName("core_datasource_engine")
public class CoreEngine implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据引擎主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据引擎名称
     */
    private String name;

    /**
     * 数据引擎描述
     */
    private String description;

    /**
     * 数据引擎类型
     */
    private String type;

    /**
     * 数据引擎连接配置详情
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
     * 创建人标识
     */
    private String createBy;

    /**
     * 数据引擎状态
     */
    private String status;

    /**
     * 是否启用数据填报功能
     */
    private Boolean enableDataFill;

    /**
     * 获取数据引擎主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据引擎主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据引擎名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置数据引擎名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取数据引擎描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置数据引擎描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取数据引擎类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据引擎类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取数据引擎连接配置详情
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * 设置数据引擎连接配置详情
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
     * 获取数据引擎状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置数据引擎状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取是否启用数据填报功能
     */
    public Boolean getEnableDataFill() {
        return enableDataFill;
    }

    /**
     * 设置是否启用数据填报功能
     */
    public void setEnableDataFill(Boolean enableDataFill) {
        this.enableDataFill = enableDataFill;
    }

    /**
     * 返回数据引擎实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreEngine{" +
        "id = " + id +
        ", name = " + name +
        ", description = " + description +
        ", type = " + type +
        ", configuration = " + configuration +
        ", createTime = " + createTime +
        ", updateTime = " + updateTime +
        ", createBy = " + createBy +
        ", status = " + status +
        ", enableDataFill = " + enableDataFill +
        "}";
    }
}
