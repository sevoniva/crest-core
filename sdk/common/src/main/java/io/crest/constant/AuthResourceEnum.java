package io.crest.constant;

/**
 * 权限资源枚举，维护资源菜单编号和业务权限标识的对应关系
 */
public enum AuthResourceEnum {

    PANEL(2, 1), SCREEN(3, 2), DATASET(5, 3), DATASOURCE(6, 4), SYSTEM(7, 0), USER(8, 5), ROLE(8, 6),  ORG(9, 7),  SYNC_DATASOURCE(23, 9),  TASK(24, 9), SUMMARY(22, 9), DATA_FILLING(60, 8), DATA_ASSET(77, 10);

    /**
     * 菜单编号，用于关联系统菜单资源
     */
    private long menuId;

    /**
     * 业务权限标识，用于区分资源类型
     */
    private int flag;

    /**
     * 获取菜单编号
     */
    public long getMenuId() {
        return menuId;
    }

    /**
     * 设置菜单编号
     */
    public void setMenuId(long menuId) {
        this.menuId = menuId;
    }

    /**
     * 获取业务权限标识
     */
    public int getFlag() {
        return flag;
    }

    /**
     * 设置业务权限标识
     */
    public void setFlag(int flag) {
        this.flag = flag;
    }

    /**
     * 构造权限资源枚举项
     */
    AuthResourceEnum(long menuId, int flag) {
        this.menuId = menuId;
        this.flag = flag;
    }
}
