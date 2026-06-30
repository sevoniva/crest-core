package io.crest.font.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 字体资源实体。
 */
@TableName("core_font_asset")
public class CoreFont implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 字体资源主键
     */
    private Long id;

    /**
     * 字体显示名称
     */
    private String name;

    /**
     * 原始字体文件名称
     */
    private String fileName;

    /**
     * 存储或转换后的字体文件名称
     */
    private String fileTransName;

    /**
     * 是否为默认字体
     */
    private Boolean isDefault;

    /**
     * 更新时间戳
     */
    private Long updateTime;

    /**
     * 是否为系统内置字体
     */
    private Boolean isBuiltin;

    /**
     * 字体文件大小
     */
    private Double size;

    /**
     * 字体文件大小单位
     */
    private String sizeType;

    /**
     * 获取字体资源主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置字体资源主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取字体显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置字体显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取原始字体文件名称
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置原始字体文件名称
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取存储或转换后的字体文件名称
     */
    public String getFileTransName() {
        return fileTransName;
    }

    /**
     * 设置存储或转换后的字体文件名称
     */
    public void setFileTransName(String fileTransName) {
        this.fileTransName = fileTransName;
    }

    /**
     * 获取是否为默认字体
     */
    public Boolean getIsDefault() {
        return isDefault;
    }

    /**
     * 设置是否为默认字体
     */
    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
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
     * 获取是否为系统内置字体
     */
    public Boolean getIsBuiltin() {
        return isBuiltin;
    }

    /**
     * 设置是否为系统内置字体
     */
    public void setIsBuiltin(Boolean isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    /**
     * 获取字体文件大小
     */
    public Double getSize() {
        return size;
    }

    /**
     * 设置字体文件大小
     */
    public void setSize(Double size) {
        this.size = size;
    }

    /**
     * 获取字体文件大小单位
     */
    public String getSizeType() {
        return sizeType;
    }

    /**
     * 设置字体文件大小单位
     */
    public void setSizeType(String sizeType) {
        this.sizeType = sizeType;
    }

    /**
     * 返回字体资源实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreFont{" +
        "id = " + id +
        ", name = " + name +
        ", fileName = " + fileName +
        ", fileTransName = " + fileTransName +
        ", isDefault = " + isDefault +
        ", updateTime = " + updateTime +
        ", isBuiltin = " + isBuiltin +
        ", size = " + size +
        ", sizeType = " + sizeType +
        "}";
    }
}
