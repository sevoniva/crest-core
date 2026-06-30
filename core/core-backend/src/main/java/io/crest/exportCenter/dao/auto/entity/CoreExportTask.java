package io.crest.exportCenter.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 导出任务实体。
 */
@TableName("core_export_task")
public class CoreExportTask implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 导出任务主键
     */
    private String id;

    /**
     * 发起导出的用户编号
     */
    private Long userId;

    /**
     * 导出文件名称
     */
    private String fileName;

    /**
     * 导出文件大小
     */
    private Double fileSize;

    /**
     * 文件大小单位
     */
    private String fileSizeUnit;

    /**
     * 导出来源资源编号
     */
    private Long exportFrom;

    /**
     * 导出任务状态
     */
    private String exportStatus;

    /**
     * 导出来源类型
     */
    private String exportFromType;

    /**
     * 导出发起时间戳
     */
    private Long exportTime;

    /**
     * 导出进度描述
     */
    private String exportProgress;

    /**
     * 执行导出任务的机器名称
     */
    private String exportMachineName;

    /**
     * 当前抢占任务的 Worker 编号
     */
    private String workerId;

    /**
     * Worker 最近心跳时间
     */
    private Long heartbeatTime;

    /**
     * 任务重试次数
     */
    private Integer retryCount;

    /**
     * 任务抢占版本号
     */
    private Long lockVersion;

    /**
     * 下次调度时间
     */
    private Long nextFireTime;

    /**
     * 最近一次投递到队列的时间
     */
    private Long lastEnqueueTime;

    /**
     * 最近一次执行错误
     */
    private String lastError;

    /**
     * 导出过滤参数
     */
    private String params;

    /**
     * 导出错误信息
     */
    private String msg;

    /**
     * 获取导出任务主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置导出任务主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取发起导出的用户编号
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置发起导出的用户编号
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取导出文件名称
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置导出文件名称
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取导出文件大小
     */
    public Double getFileSize() {
        return fileSize;
    }

    /**
     * 设置导出文件大小
     */
    public void setFileSize(Double fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 获取文件大小单位
     */
    public String getFileSizeUnit() {
        return fileSizeUnit;
    }

    /**
     * 设置文件大小单位
     */
    public void setFileSizeUnit(String fileSizeUnit) {
        this.fileSizeUnit = fileSizeUnit;
    }

    /**
     * 获取导出来源资源编号
     */
    public Long getExportFrom() {
        return exportFrom;
    }

    /**
     * 设置导出来源资源编号
     */
    public void setExportFrom(Long exportFrom) {
        this.exportFrom = exportFrom;
    }

    /**
     * 获取导出任务状态
     */
    public String getExportStatus() {
        return exportStatus;
    }

    /**
     * 设置导出任务状态
     */
    public void setExportStatus(String exportStatus) {
        this.exportStatus = exportStatus;
    }

    /**
     * 获取导出来源类型
     */
    public String getExportFromType() {
        return exportFromType;
    }

    /**
     * 设置导出来源类型
     */
    public void setExportFromType(String exportFromType) {
        this.exportFromType = exportFromType;
    }

    /**
     * 获取导出发起时间戳
     */
    public Long getExportTime() {
        return exportTime;
    }

    /**
     * 设置导出发起时间戳
     */
    public void setExportTime(Long exportTime) {
        this.exportTime = exportTime;
    }

    /**
     * 获取导出进度描述
     */
    public String getExportProgress() {
        return exportProgress;
    }

    /**
     * 设置导出进度描述
     */
    public void setExportProgress(String exportProgress) {
        this.exportProgress = exportProgress;
    }

    /**
     * 获取执行导出任务的机器名称
     */
    public String getExportMachineName() {
        return exportMachineName;
    }

    /**
     * 设置执行导出任务的机器名称
     */
    public void setExportMachineName(String exportMachineName) {
        this.exportMachineName = exportMachineName;
    }

    /**
     * 获取当前抢占任务的 Worker 编号
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * 设置当前抢占任务的 Worker 编号
     */
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * 获取 Worker 最近心跳时间
     */
    public Long getHeartbeatTime() {
        return heartbeatTime;
    }

    /**
     * 设置 Worker 最近心跳时间
     */
    public void setHeartbeatTime(Long heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }

    /**
     * 获取任务重试次数
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 设置任务重试次数
     */
    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * 获取任务抢占版本号
     */
    public Long getLockVersion() {
        return lockVersion;
    }

    /**
     * 设置任务抢占版本号
     */
    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }

    /**
     * 获取下次调度时间
     */
    public Long getNextFireTime() {
        return nextFireTime;
    }

    /**
     * 设置下次调度时间
     */
    public void setNextFireTime(Long nextFireTime) {
        this.nextFireTime = nextFireTime;
    }

    /**
     * 获取最近一次投递到队列的时间
     */
    public Long getLastEnqueueTime() {
        return lastEnqueueTime;
    }

    /**
     * 设置最近一次投递到队列的时间
     */
    public void setLastEnqueueTime(Long lastEnqueueTime) {
        this.lastEnqueueTime = lastEnqueueTime;
    }

    /**
     * 获取最近一次执行错误
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 设置最近一次执行错误
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * 获取导出过滤参数
     */
    public String getParams() {
        return params;
    }

    /**
     * 设置导出过滤参数
     */
    public void setParams(String params) {
        this.params = params;
    }

    /**
     * 获取导出错误信息
     */
    public String getMsg() {
        return msg;
    }

    /**
     * 设置导出错误信息
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    /**
     * 返回导出任务实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreExportTask{" +
        "id = " + id +
        ", userId = " + userId +
        ", fileName = " + fileName +
        ", fileSize = " + fileSize +
        ", fileSizeUnit = " + fileSizeUnit +
        ", exportFrom = " + exportFrom +
        ", exportStatus = " + exportStatus +
        ", exportFromType = " + exportFromType +
        ", exportTime = " + exportTime +
        ", exportProgress = " + exportProgress +
        ", exportMachineName = " + exportMachineName +
        ", workerId = " + workerId +
        ", heartbeatTime = " + heartbeatTime +
        ", retryCount = " + retryCount +
        ", lockVersion = " + lockVersion +
        ", nextFireTime = " + nextFireTime +
        ", lastEnqueueTime = " + lastEnqueueTime +
        ", lastError = " + lastError +
        ", params = " + params +
        ", msg = " + msg +
        "}";
    }
}
