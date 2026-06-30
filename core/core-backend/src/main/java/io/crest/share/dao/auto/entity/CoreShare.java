package io.crest.share.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 分享链接实体。
 */
@TableName("core_share_link")
public class CoreShare implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 分享记录主键
     */
    private Long id;

    /**
     * 创建人用户编号
     */
    private Long creator;

    /**
     * 创建时间戳
     */
    private Long time;

    /**
     * 过期时间戳
     */
    private Long exp;

    /**
     * 分享链接唯一标识
     */
    private String uuid;

    /**
     * 分享访问密码
     */
    private String pwd;

    /**
     * 被分享资源编号
     */
    private Long resourceId;

    /**
     * 所属组织编号
     */
    private Long oid;

    /**
     * 分享资源业务类型
     */
    private Integer type;

    /**
     * 是否自动生成访问密码
     */
    private Boolean autoPwd;

    /**
     * 是否要求携带访问票据
     */
    private Boolean ticketRequire;

    /**
     * 获取分享记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置分享记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取创建人用户编号
     */
    public Long getCreator() {
        return creator;
    }

    /**
     * 设置创建人用户编号
     */
    public void setCreator(Long creator) {
        this.creator = creator;
    }

    /**
     * 获取创建时间戳
     */
    public Long getTime() {
        return time;
    }

    /**
     * 设置创建时间戳
     */
    public void setTime(Long time) {
        this.time = time;
    }

    /**
     * 获取过期时间戳
     */
    public Long getExp() {
        return exp;
    }

    /**
     * 设置过期时间戳
     */
    public void setExp(Long exp) {
        this.exp = exp;
    }

    /**
     * 获取分享链接唯一标识
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * 设置分享链接唯一标识
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * 获取分享访问密码
     */
    public String getPwd() {
        return pwd;
    }

    /**
     * 设置分享访问密码
     */
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    /**
     * 获取被分享资源编号
     */
    public Long getResourceId() {
        return resourceId;
    }

    /**
     * 设置被分享资源编号
     */
    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * 获取所属组织编号
     */
    public Long getOid() {
        return oid;
    }

    /**
     * 设置所属组织编号
     */
    public void setOid(Long oid) {
        this.oid = oid;
    }

    /**
     * 获取分享资源业务类型
     */
    public Integer getType() {
        return type;
    }

    /**
     * 设置分享资源业务类型
     */
    public void setType(Integer type) {
        this.type = type;
    }

    /**
     * 获取是否自动生成访问密码
     */
    public Boolean getAutoPwd() {
        return autoPwd;
    }

    /**
     * 设置是否自动生成访问密码
     */
    public void setAutoPwd(Boolean autoPwd) {
        this.autoPwd = autoPwd;
    }

    /**
     * 获取是否要求携带访问票据
     */
    public Boolean getTicketRequire() {
        return ticketRequire;
    }

    /**
     * 设置是否要求携带访问票据
     */
    public void setTicketRequire(Boolean ticketRequire) {
        this.ticketRequire = ticketRequire;
    }

    /**
     * 返回分享实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreShare{" +
        "id = " + id +
        ", creator = " + creator +
        ", time = " + time +
        ", exp = " + exp +
        ", uuid = " + uuid +
        ", pwd = " + pwd +
        ", resourceId = " + resourceId +
        ", oid = " + oid +
        ", type = " + type +
        ", autoPwd = " + autoPwd +
        ", ticketRequire = " + ticketRequire +
        "}";
    }
}
