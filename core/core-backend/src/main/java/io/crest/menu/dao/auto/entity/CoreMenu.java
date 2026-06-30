package io.crest.menu.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 菜单资源实体。
 */
@TableName("core_iam_menu")
public class CoreMenu implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 菜单主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父级菜单编号
     */
    private Long pid;

    /**
     * 菜单类型
     */
    private Integer type;

    /**
     * 菜单显示名称
     */
    private String name;

    /**
     * 前端组件路径
     */
    private String component;

    /**
     * 菜单排序值
     */
    private Integer menuSort;

    /**
     * 菜单图标标识
     */
    private String icon;

    /**
     * 菜单访问路径
     */
    private String path;

    /**
     * 是否在菜单中隐藏
     */
    private Boolean hidden;

    /**
     * 是否在主布局中展示
     */
    private Boolean inLayout;

    /**
     * 是否参与权限授权
     */
    private Boolean auth;

    /**
     * 获取菜单主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置菜单主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取父级菜单编号
     */
    public Long getPid() {
        return pid;
    }

    /**
     * 设置父级菜单编号
     */
    public void setPid(Long pid) {
        this.pid = pid;
    }

    /**
     * 获取菜单类型
     */
    public Integer getType() {
        return type;
    }

    /**
     * 设置菜单类型
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * 获取菜单显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置菜单显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取前端组件路径
     */
    public String getComponent() {
        return component;
    }

    /**
     * 设置前端组件路径
     */
    public void setComponent(String component) {
        this.component = component;
    }

    /**
     * 获取菜单排序值
     */
    public Integer getMenuSort() {
        return menuSort;
    }

    /**
     * 设置菜单排序值
     */
    public void setMenuSort(Integer menuSort) {
        this.menuSort = menuSort;
    }

    /**
     * 获取菜单图标标识
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 设置菜单图标标识
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * 获取菜单访问路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置菜单访问路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取菜单隐藏状态
     */
    public Boolean getHidden() {
        return hidden;
    }

    /**
     * 设置菜单隐藏状态
     */
    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * 获取主布局展示状态
     */
    public Boolean getInLayout() {
        return inLayout;
    }

    /**
     * 设置主布局展示状态
     */
    public void setInLayout(Boolean inLayout) {
        this.inLayout = inLayout;
    }

    /**
     * 获取菜单授权状态
     */
    public Boolean getAuth() {
        return auth;
    }

    /**
     * 设置菜单授权状态
     */
    public void setAuth(Boolean auth) {
        this.auth = auth;
    }

    /**
     * 返回菜单实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreMenu{" +
        "id = " + id +
        ", pid = " + pid +
        ", type = " + type +
        ", name = " + name +
        ", component = " + component +
        ", menuSort = " + menuSort +
        ", icon = " + icon +
        ", path = " + path +
        ", hidden = " + hidden +
        ", inLayout = " + inLayout +
        ", auth = " + auth +
        "}";
    }
}
