package io.crest.datasource.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据源驱动实体。
 */
@TableName("core_datasource_driver")
public class CoreDriver implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 驱动记录主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 驱动名称
     */
    private String name;

    /**
     * 驱动创建时间
     */
    private Long createTime;

    /**
     * 适配的数据源类型
     */
    private String type;

    /**
     * JDBC 驱动类名
     */
    private String driverClass;

    /**
     * 驱动描述
     */
    private String description;

    /**
     * 获取驱动记录主键
     *
     * @return 驱动记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置驱动记录主键
     *
     * @param id 驱动记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取驱动名称
     *
     * @return 驱动名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置驱动名称
     *
     * @param name 驱动名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取驱动创建时间
     *
     * @return 驱动创建时间
     */
    public Long getCreateTime() {
        return createTime;
    }

    /**
     * 设置驱动创建时间
     *
     * @param createTime 驱动创建时间
     */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取适配的数据源类型
     *
     * @return 适配的数据源类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置适配的数据源类型
     *
     * @param type 适配的数据源类型
     */
    public void setType(String type) {
        this.type = type;
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
     * 获取驱动描述
     *
     * @return 驱动描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置驱动描述
     *
     * @param description 驱动描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 返回驱动记录的调试字符串
     *
     * @return 驱动记录的字符串表示
     */
    @Override
    public String toString() {
        return "CoreDriver{" +
        "id = " + id +
        ", name = " + name +
        ", createTime = " + createTime +
        ", type = " + type +
        ", driverClass = " + driverClass +
        ", description = " + description +
        "}";
    }
}
