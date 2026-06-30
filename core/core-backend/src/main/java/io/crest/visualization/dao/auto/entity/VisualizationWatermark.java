package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化水印配置实体。
 */
@TableName("core_visualization_watermark")
public class VisualizationWatermark implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 版本号
     */
    private String version;

    /**
     * 设置内容
     */
    private String settingContent;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 获取水印配置主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置水印配置主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取水印配置版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置水印配置版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取水印设置内容
     */
    public String getSettingContent() {
        return settingContent;
    }

    /**
     * 设置水印设置内容
     */
    public void setSettingContent(String settingContent) {
        this.settingContent = settingContent;
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
     * 返回水印配置的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationWatermark{" +
        "id = " + id +
        ", version = " + version +
        ", settingContent = " + settingContent +
        ", createBy = " + createBy +
        ", createTime = " + createTime +
        "}";
    }
}
