package io.crest.template.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内置模板初始化版本实体。
 */
@TableName("core_template_init_history")
public class TemplateVersion implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 模板初始化脚本安装顺序
     */
    @TableId("installed_rank")
    private Integer installedRank;

    /**
     * 模板初始化版本号
     */
    private String version;

    /**
     * 初始化脚本描述
     */
    private String description;

    /**
     * 初始化脚本类型
     */
    private String type;

    /**
     * 初始化脚本名称
     */
    private String script;

    /**
     * 初始化脚本校验和
     */
    private Integer checksum;

    /**
     * 执行初始化脚本的用户
     */
    private String installedBy;

    /**
     * 初始化脚本执行时间
     */
    private LocalDateTime installedOn;

    /**
     * 初始化脚本执行耗时
     */
    private Integer executionTime;

    /**
     * 初始化脚本是否执行成功
     */
    private Boolean success;

    /**
     * 获取模板初始化脚本安装顺序
     */
    public Integer getInstalledRank() {
        return installedRank;
    }

    /**
     * 设置模板初始化脚本安装顺序
     */
    public void setInstalledRank(Integer installedRank) {
        this.installedRank = installedRank;
    }

    /**
     * 获取模板初始化版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置模板初始化版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取初始化脚本描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置初始化脚本描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取初始化脚本类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置初始化脚本类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取初始化脚本名称
     */
    public String getScript() {
        return script;
    }

    /**
     * 设置初始化脚本名称
     */
    public void setScript(String script) {
        this.script = script;
    }

    /**
     * 获取初始化脚本校验和
     */
    public Integer getChecksum() {
        return checksum;
    }

    /**
     * 设置初始化脚本校验和
     */
    public void setChecksum(Integer checksum) {
        this.checksum = checksum;
    }

    /**
     * 获取执行初始化脚本的用户
     */
    public String getInstalledBy() {
        return installedBy;
    }

    /**
     * 设置执行初始化脚本的用户
     */
    public void setInstalledBy(String installedBy) {
        this.installedBy = installedBy;
    }

    /**
     * 获取初始化脚本执行时间
     */
    public LocalDateTime getInstalledOn() {
        return installedOn;
    }

    /**
     * 设置初始化脚本执行时间
     */
    public void setInstalledOn(LocalDateTime installedOn) {
        this.installedOn = installedOn;
    }

    /**
     * 获取初始化脚本执行耗时
     */
    public Integer getExecutionTime() {
        return executionTime;
    }

    /**
     * 设置初始化脚本执行耗时
     */
    public void setExecutionTime(Integer executionTime) {
        this.executionTime = executionTime;
    }

    /**
     * 获取初始化脚本是否执行成功
     */
    public Boolean getSuccess() {
        return success;
    }

    /**
     * 设置初始化脚本是否执行成功
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * 返回模板初始化版本实体的调试字符串
     */
    @Override
    public String toString() {
        return "TemplateVersion{" +
        "installedRank = " + installedRank +
        ", version = " + version +
        ", description = " + description +
        ", type = " + type +
        ", script = " + script +
        ", checksum = " + checksum +
        ", installedBy = " + installedBy +
        ", installedOn = " + installedOn +
        ", executionTime = " + executionTime +
        ", success = " + success +
        "}";
    }
}
