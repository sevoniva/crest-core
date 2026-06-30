package io.crest.startup.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 系统启动任务实体。
 */
@TableName("core_system_startup_job")
public class CoreSysStartupJob implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private String id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 获取启动任务主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置启动任务主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取启动任务名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置启动任务名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取启动任务状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置启动任务状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 返回启动任务的调试字符串
     */
    @Override
    public String toString() {
        return "CoreSysStartupJob{" +
        "id = " + id +
        ", name = " + name +
        ", status = " + status +
        "}";
    }
}
