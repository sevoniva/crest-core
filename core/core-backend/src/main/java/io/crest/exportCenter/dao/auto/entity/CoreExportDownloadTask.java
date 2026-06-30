package io.crest.exportCenter.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 导出下载任务实体。
 */
@TableName("core_export_download_task")
public class CoreExportDownloadTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Long createTime;

    private Long validTime;

    /**
     * 获取下载任务主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置下载任务主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取任务创建时间
     */
    public Long getCreateTime() {
        return createTime;
    }

    /**
     * 设置任务创建时间
     */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取任务有效期时间
     */
    public Long getValidTime() {
        return validTime;
    }

    /**
     * 设置任务有效期时间
     */
    public void setValidTime(Long validTime) {
        this.validTime = validTime;
    }

    /**
     * 返回导出下载任务的调试字符串
     */
    @Override
    public String toString() {
        return "CoreExportDownloadTask{" +
        "id = " + id +
        ", createTime = " + createTime +
        ", validTime = " + validTime +
        "}";
    }
}
