package io.crest.share.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 分享访问票据实体。
 */
@TableName("core_share_ticket")
public class CoreShareTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分享票据记录主键
     */
    private Long id;

    /**
     * 分享资源 UUID
     */
    private String uuid;

    /**
     * 分享访问 ticket
     */
    private String ticket;

    /**
     * 分享访问 ticket 有效期
     */
    private Long exp;

    /**
     * 分享访问 ticket 参数
     */
    private String args;

    /**
     * 分享链接首次访问时间
     */
    private Long accessTime;

    /**
     * 获取分享票据记录主键
     *
     * @return 分享票据记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置分享票据记录主键
     *
     * @param id 分享票据记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取分享资源 UUID
     *
     * @return 分享资源 UUID
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * 设置分享资源 UUID
     *
     * @param uuid 分享资源 UUID
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * 获取分享访问 ticket
     *
     * @return 分享访问 ticket
     */
    public String getTicket() {
        return ticket;
    }

    /**
     * 设置分享访问 ticket
     *
     * @param ticket 分享访问 ticket
     */
    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    /**
     * 获取分享访问 ticket 有效期
     *
     * @return 分享访问 ticket 有效期
     */
    public Long getExp() {
        return exp;
    }

    /**
     * 设置分享访问 ticket 有效期
     *
     * @param exp 分享访问 ticket 有效期
     */
    public void setExp(Long exp) {
        this.exp = exp;
    }

    /**
     * 获取分享访问 ticket 参数
     *
     * @return 分享访问 ticket 参数
     */
    public String getArgs() {
        return args;
    }

    /**
     * 设置分享访问 ticket 参数
     *
     * @param args 分享访问 ticket 参数
     */
    public void setArgs(String args) {
        this.args = args;
    }

    /**
     * 获取分享链接首次访问时间
     *
     * @return 分享链接首次访问时间
     */
    public Long getAccessTime() {
        return accessTime;
    }

    /**
     * 设置分享链接首次访问时间
     *
     * @param accessTime 分享链接首次访问时间
     */
    public void setAccessTime(Long accessTime) {
        this.accessTime = accessTime;
    }

    /**
     * 返回分享票据记录的调试字符串
     *
     * @return 分享票据记录的字符串表示
     */
    @Override
    public String toString() {
        return "CoreShareTicket{" +
        "id = " + id +
        ", uuid = " + uuid +
        ", ticket = " + ticket +
        ", exp = " + exp +
        ", args = " + args +
        ", accessTime = " + accessTime +
        "}";
    }
}
