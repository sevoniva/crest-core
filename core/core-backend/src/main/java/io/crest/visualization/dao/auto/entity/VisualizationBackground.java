package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化背景资源实体。
 */
@TableName("core_visualization_background")
public class VisualizationBackground implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 背景资源主键
     */
    private String id;

    /**
     * 背景资源名称
     */
    private String name;

    /**
     * 背景资源分类
     */
    private String classification;

    /**
     * 背景资源内容配置
     */
    private String content;

    /**
     * 背景资源备注
     */
    private String remark;

    /**
     * 背景资源排序值
     */
    private Integer sort;

    /**
     * 上传时间戳
     */
    private Long uploadTime;

    /**
     * 背景资源基础访问地址
     */
    private String baseUrl;

    /**
     * 背景资源完整访问地址
     */
    private String url;

    /**
     * 获取背景资源主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置背景资源主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取背景资源名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置背景资源名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取背景资源分类
     */
    public String getClassification() {
        return classification;
    }

    /**
     * 设置背景资源分类
     */
    public void setClassification(String classification) {
        this.classification = classification;
    }

    /**
     * 获取背景资源内容配置
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置背景资源内容配置
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取背景资源备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置背景资源备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取背景资源排序值
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * 设置背景资源排序值
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * 获取上传时间戳
     */
    public Long getUploadTime() {
        return uploadTime;
    }

    /**
     * 设置上传时间戳
     */
    public void setUploadTime(Long uploadTime) {
        this.uploadTime = uploadTime;
    }

    /**
     * 获取背景资源基础访问地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置背景资源基础访问地址
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取背景资源完整访问地址
     */
    public String getUrl() {
        return url;
    }

    /**
     * 设置背景资源完整访问地址
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * 返回背景资源实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationBackground{" +
        "id = " + id +
        ", name = " + name +
        ", classification = " + classification +
        ", content = " + content +
        ", remark = " + remark +
        ", sort = " + sort +
        ", uploadTime = " + uploadTime +
        ", baseUrl = " + baseUrl +
        ", url = " + url +
        "}";
    }
}
