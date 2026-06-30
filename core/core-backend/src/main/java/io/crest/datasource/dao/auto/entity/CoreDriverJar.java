package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据源驱动 Jar 实体。
 */
@TableName("core_datasource_driver_jar")
public class CoreDriverJar implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 驱动 Jar 记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据源驱动主键
     */
    private String driverId;

    /**
     * 驱动 Jar 文件名称
     */
    private String fileName;

    /**
     * 驱动 Jar 版本号
     */
    private String version;

    /**
     * JDBC 驱动类名
     */
    private String driverClass;

    /**
     * 转换后的文件名称
     */
    private String transName;

    /**
     * 是否使用转换后的文件名称
     */
    private Boolean isTransName;

    /**
     * 获取驱动 Jar 记录主键
     *
     * @return 驱动 Jar 记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置驱动 Jar 记录主键
     *
     * @param id 驱动 Jar 记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据源驱动主键
     *
     * @return 数据源驱动主键
     */
    public String getDriverId() {
        return driverId;
    }

    /**
     * 设置数据源驱动主键
     *
     * @param driverId 数据源驱动主键
     */
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    /**
     * 获取驱动 Jar 文件名称
     *
     * @return 驱动 Jar 文件名称
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置驱动 Jar 文件名称
     *
     * @param fileName 驱动 Jar 文件名称
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取驱动 Jar 版本号
     *
     * @return 驱动 Jar 版本号
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置驱动 Jar 版本号
     *
     * @param version 驱动 Jar 版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取 JDBC 驱动类名
     *
     * @return JDBC 驱动类名
     */
    public String getDriverClass() {
        return driverClass;
    }

    /**
     * 设置 JDBC 驱动类名
     *
     * @param driverClass JDBC 驱动类名
     */
    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    /**
     * 获取转换后的文件名称
     *
     * @return 转换后的文件名称
     */
    public String getTransName() {
        return transName;
    }

    /**
     * 设置转换后的文件名称
     *
     * @param transName 转换后的文件名称
     */
    public void setTransName(String transName) {
        this.transName = transName;
    }

    /**
     * 获取是否使用转换后的文件名称
     *
     * @return 是否使用转换后的文件名称
     */
    public Boolean getIsTransName() {
        return isTransName;
    }

    /**
     * 设置是否使用转换后的文件名称
     *
     * @param isTransName 是否使用转换后的文件名称
     */
    public void setIsTransName(Boolean isTransName) {
        this.isTransName = isTransName;
    }

    /**
     * 返回驱动 Jar 记录的调试字符串
     *
     * @return 驱动 Jar 记录的字符串表示
     */
    @Override
    public String toString() {
        return "CoreDriverJar{" +
        "id = " + id +
        ", driverId = " + driverId +
        ", fileName = " + fileName +
        ", version = " + version +
        ", driverClass = " + driverClass +
        ", transName = " + transName +
        ", isTransName = " + isTransName +
        "}";
    }
}
