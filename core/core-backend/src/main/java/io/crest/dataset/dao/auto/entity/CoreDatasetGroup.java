package io.crest.dataset.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;

/**
 * 数据集分组实体。
 */
@TableName("core_dataset")
public class CoreDatasetGroup implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据集分组主键
     */
    private Long id;

    /**
     * 数据集分组名称
     */
    private String name;

    /**
     * 父级分组编号
     */
    private Long pid;

    /**
     * 当前分组层级
     */
    private Integer level;

    /**
     * 节点类型，区分文件夹和数据集
     */
    private String nodeType;

    /**
     * 数据集类型，区分普通 SQL 和关联数据集
     */
    private String type;

    /**
     * 连接模式，0 表示直连，1 表示同步落表
     */
    private Integer mode;

    /**
     * 关联关系树配置
     */
    private String info;

    /**
     * 创建人标识
     */
    private String createBy;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 调度实例编号
     */
    @TableField("scheduler_fire_instance_id")
    private String schedulerFireInstanceId;

    /**
     * 同步状态
     */
    private String syncStatus;

    /**
     * 更新人标识
     */
    private String updateBy;

    /**
     * 最后同步时间戳
     */
    private Long lastUpdateTime;

    /**
     * 关联数据集 SQL
     */
    private String unionSql;

    /**
     * 是否跨数据源
     */
    private Boolean isCross;

    /**
     * 获取数据集分组主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据集分组主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据集分组名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置数据集分组名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取父级分组编号
     */
    public Long getPid() {
        return pid;
    }

    /**
     * 设置父级分组编号
     */
    public void setPid(Long pid) {
        this.pid = pid;
    }

    /**
     * 获取当前分组层级
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置当前分组层级
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * 获取节点类型
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * 设置节点类型
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * 获取数据集类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据集类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取连接模式
     */
    public Integer getMode() {
        return mode;
    }

    /**
     * 设置连接模式
     */
    public void setMode(Integer mode) {
        this.mode = mode;
    }

    /**
     * 获取关联关系树配置
     */
    public String getInfo() {
        return info;
    }

    /**
     * 设置关联关系树配置
     */
    public void setInfo(String info) {
        this.info = info;
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
     * 获取同步状态
     */
    public String getSyncStatus() {
        return syncStatus;
    }

    /**
     * 设置同步状态
     */
    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    /**
     * 获取更新人标识
     */
    public String getUpdateBy() {
        return updateBy;
    }

    /**
     * 设置更新人标识
     */
    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    /**
     * 获取最后同步时间戳
     */
    public Long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * 设置最后同步时间戳
     */
    public void setLastUpdateTime(Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * 获取关联数据集 SQL
     */
    public String getUnionSql() {
        return unionSql;
    }

    /**
     * 设置关联数据集 SQL
     */
    public void setUnionSql(String unionSql) {
        this.unionSql = unionSql;
    }

    /**
     * 获取是否跨数据源
     */
    public Boolean getIsCross() {
        return isCross;
    }

    /**
     * 设置是否跨数据源
     */
    public void setIsCross(Boolean isCross) {
        this.isCross = isCross;
    }

    @Override
    public String toString() {
        return "CoreDatasetGroup{" +
        "id = " + id +
        ", name = " + name +
        ", pid = " + pid +
        ", level = " + level +
        ", nodeType = " + nodeType +
        ", type = " + type +
        ", mode = " + mode +
        ", info = " + info +
        ", createBy = " + createBy +
        ", createTime = " + createTime +
        ", schedulerFireInstanceId = " + schedulerFireInstanceId +
        ", syncStatus = " + syncStatus +
        ", updateBy = " + updateBy +
        ", lastUpdateTime = " + lastUpdateTime +
        ", unionSql = " + unionSql +
        ", isCross = " + isCross +
        "}";
    }
}
