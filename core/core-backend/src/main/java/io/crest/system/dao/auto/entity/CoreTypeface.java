package io.crest.system.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 字形资源实体。
 */
@TableName("core_font_asset")
public class CoreTypeface implements Serializable {

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
     * 字体文件原始名称
     */
    private String fileName;

    /**
     * 字体文件转换后的存储名称
     */
    private String fileTransName;

    /**
     * 是否为默认字体
     */
    private Boolean isDefault;

    /**
     * 获取字体资源主键
     *
     * @return 字体资源主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置字体资源主键
     *
     * @param id 字体资源主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取字体显示名称
     *
     * @return 字体显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置字体显示名称
     *
     * @param name 字体显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取字体文件原始名称
     *
     * @return 字体文件原始名称
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置字体文件原始名称
     *
     * @param fileName 字体文件原始名称
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取字体文件转换后的存储名称
     *
     * @return 字体文件转换后的存储名称
     */
    public String getFileTransName() {
        return fileTransName;
    }

    /**
     * 设置字体文件转换后的存储名称
     *
     * @param fileTransName 字体文件转换后的存储名称
     */
    public void setFileTransName(String fileTransName) {
        this.fileTransName = fileTransName;
    }

    /**
     * 获取是否为默认字体
     *
     * @return 是否为默认字体
     */
    public Boolean getIsDefault() {
        return isDefault;
    }

    /**
     * 设置是否为默认字体
     *
     * @param isDefault 是否为默认字体
     */
    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * 返回字体资源的调试字符串
     *
     * @return 字体资源的字符串表示
     */
    @Override
    public String toString() {
        return "CoreTypeface{" +
        "id = " + id +
        ", name = " + name +
        ", fileName = " + fileName +
        ", fileTransName = " + fileTransName +
        ", isDefault = " + isDefault +
        "}";
    }
}
