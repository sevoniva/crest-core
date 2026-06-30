package io.crest.system.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 系统配置实体。
 */
@TableName("core_system_setting")
public class CoreSysSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统配置主键
     */
    private Long id;

    /**
     * 系统配置键
     */
    private String pkey;

    /**
     * 系统配置值
     */
    private String pval;

    /**
     * 系统配置类型
     */
    private String type;

    /**
     * 系统配置排序值
     */
    private Integer sort;

    /**
     * 获取系统配置主键
     *
     * @return 系统配置主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置系统配置主键
     *
     * @param id 系统配置主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取系统配置键
     *
     * @return 系统配置键
     */
    public String getPkey() {
        return pkey;
    }

    /**
     * 设置系统配置键
     *
     * @param pkey 系统配置键
     */
    public void setPkey(String pkey) {
        this.pkey = pkey;
    }

    /**
     * 获取系统配置值
     *
     * @return 系统配置值
     */
    public String getPval() {
        return pval;
    }

    /**
     * 设置系统配置值
     *
     * @param pval 系统配置值
     */
    public void setPval(String pval) {
        this.pval = pval;
    }

    /**
     * 获取系统配置类型
     *
     * @return 系统配置类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置系统配置类型
     *
     * @param type 系统配置类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取系统配置排序值
     *
     * @return 系统配置排序值
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * 设置系统配置排序值
     *
     * @param sort 系统配置排序值
     */
    public void setSort(Integer sort) {
        this.sort = sort;
    }

    /**
     * 返回系统配置的调试字符串
     *
     * @return 系统配置的字符串表示
     */
    @Override
    public String toString() {
        return "CoreSysSetting{" +
        "id = " + id +
        ", pkey = " + pkey +
        ", pval = " + pval +
        ", type = " + type +
        ", sort = " + sort +
        "}";
    }
}
