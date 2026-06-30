package io.crest.api.visualization.vo;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 定时报告过滤条件配置
 */
public class VisualizationReportFilterVO implements Serializable {
    /**
     * 序列化版本号，保证对象跨服务传输时结构一致
     */
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    private Long id;

    /**
     * 定时报告id
     */
    private Long reportId;

    /**
     * 任务id
     */
    private Long taskId;

    /**
     * 资源id
     */
    private Long resourceId;

    /**
     * 资源类型
     */
    private String dvType;

    /**
     * 组件id
     */
    private Long componentId;

    /**
     * 过滤项id
     */
    private Long filterId;

    /**
     * 过滤组件内容
     */
    private String filterInfo;

    /**
     * 过滤组件版本
     */
    private Integer filterVersion;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 返回过滤条件记录编号
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置过滤条件记录编号
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回所属定时报告编号
     */
    public Long getReportId() {
        return reportId;
    }

    /**
     * 设置所属定时报告编号
     */
    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    /**
     * 返回定时任务编号
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 设置定时任务编号
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * 返回被过滤资源编号
     */
    public Long getResourceId() {
        return resourceId;
    }

    /**
     * 设置被过滤资源编号
     */
    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * 返回资源类型
     */
    public String getDvType() {
        return dvType;
    }

    /**
     * 设置资源类型
     */
    public void setDvType(String dvType) {
        this.dvType = dvType;
    }

    /**
     * 返回过滤组件编号
     */
    public Long getComponentId() {
        return componentId;
    }

    /**
     * 设置过滤组件编号
     */
    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    /**
     * 返回过滤项编号
     */
    public Long getFilterId() {
        return filterId;
    }

    /**
     * 设置过滤项编号
     */
    public void setFilterId(Long filterId) {
        this.filterId = filterId;
    }

    /**
     * 返回过滤组件配置内容
     */
    public String getFilterInfo() {
        return filterInfo;
    }

    /**
     * 设置过滤组件配置内容
     */
    public void setFilterInfo(String filterInfo) {
        this.filterInfo = filterInfo;
    }

    /**
     * 返回过滤配置版本
     */
    public Integer getFilterVersion() {
        return filterVersion;
    }

    /**
     * 设置过滤配置版本
     */
    public void setFilterVersion(Integer filterVersion) {
        this.filterVersion = filterVersion;
    }

    /**
     * 返回创建时间戳
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
     * 返回创建用户标识
     */
    public String getCreateUser() {
        return createUser;
    }

    /**
     * 设置创建用户标识
     */
    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    @Override
    /**
     * 输出过滤条件的调试字符串
     */
    public String toString() {
        return "VisualizationReportFilter{" +
                "id = " + id +
                ", reportId = " + reportId +
                ", taskId = " + taskId +
                ", resourceId = " + resourceId +
                ", dvType = " + dvType +
                ", componentId = " + componentId +
                ", filterId = " + filterId +
                ", filterInfo = " + filterInfo +
                ", filterVersion = " + filterVersion +
                ", createTime = " + createTime +
                ", createUser = " + createUser +
                "}";
    }
}
