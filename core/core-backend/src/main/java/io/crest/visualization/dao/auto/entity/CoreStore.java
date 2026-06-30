package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 工作台收藏资源实体。
 */
@TableName("core_workspace_favorite_resource")
public class CoreStore implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 收藏记录主键
     */
    private Long id;

    /**
     * 收藏资源 ID
     */
    private Long resourceId;

    /**
     * 收藏用户 ID
     */
    private Long uid;

    /**
     * 收藏资源类型
     */
    private Integer resourceType;

    /**
     * 收藏时间
     */
    private Long time;

    /**
     * 获取收藏记录主键
     *
     * @return 收藏记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置收藏记录主键
     *
     * @param id 收藏记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取收藏资源 ID
     *
     * @return 收藏资源 ID
     */
    public Long getResourceId() {
        return resourceId;
    }

    /**
     * 设置收藏资源 ID
     *
     * @param resourceId 收藏资源 ID
     */
    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * 获取收藏用户 ID
     *
     * @return 收藏用户 ID
     */
    public Long getUid() {
        return uid;
    }

    /**
     * 设置收藏用户 ID
     *
     * @param uid 收藏用户 ID
     */
    public void setUid(Long uid) {
        this.uid = uid;
    }

    /**
     * 获取收藏资源类型
     *
     * @return 收藏资源类型
     */
    public Integer getResourceType() {
        return resourceType;
    }

    /**
     * 设置收藏资源类型
     *
     * @param resourceType 收藏资源类型
     */
    public void setResourceType(Integer resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * 获取收藏时间
     *
     * @return 收藏时间
     */
    public Long getTime() {
        return time;
    }

    /**
     * 设置收藏时间
     *
     * @param time 收藏时间
     */
    public void setTime(Long time) {
        this.time = time;
    }

    /**
     * 返回收藏记录的调试字符串
     *
     * @return 收藏记录的字符串表示
     */
    @Override
    public String toString() {
        return "CoreStore{" +
        "id = " + id +
        ", resourceId = " + resourceId +
        ", uid = " + uid +
        ", resourceType = " + resourceType +
        ", time = " + time +
        "}";
    }
}
