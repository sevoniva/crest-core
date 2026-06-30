package io.crest.exportCenter.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.exportCenter.ExportCenterApi;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.exception.CrestException;
import io.crest.exportCenter.manage.ExportCenterManage;
import io.crest.exportCenter.util.ExportCenterUtils;
import io.crest.log.CrestAudit;
import io.crest.model.ExportTaskDTO;
import io.crest.result.ResultMessage;
import io.crest.utils.JsonUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/export-center")
@Transactional(rollbackFor = Exception.class)
// 提供导出任务查询、下载和重试接口
public class ExportCenterServer implements ExportCenterApi {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String DEFAULT_STATUS = "ALL";
    private static final String DOWNLOAD_FORBIDDEN_MESSAGE = "下载链接无效或已过期";
    private static final Set<String> EXPORT_STATUSES = Set.of("SUCCESS", "FAILED", "PENDING", "IN_PROGRESS", "ALL");

    @Resource
    private ExportCenterManage exportCenterManage;

    // 查询导出任务状态统计
    @Override
    public Map<String, Long> exportTasks() {
        return exportCenterManage.exportTasks();
    }

    // 分页查询导出任务
    @Override
    public IPage<ExportTaskDTO> pager(String goPage, String pageSize, String status) {
        Page<ExportTaskDTO> page = new Page<>(
                positiveIntOrDefault(goPage, DEFAULT_PAGE),
                positiveIntOrDefault(pageSize, DEFAULT_PAGE_SIZE)
        );
        page.setOptimizeCountSql(false);
        return exportCenterManage.pager(page, normalizeStatus(status));
    }

    // 将字符串页码解析为正整数
    private int positiveIntOrDefault(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    // 规范化导出任务状态筛选值
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "undefined".equalsIgnoreCase(status) || "null".equalsIgnoreCase(status)) {
            return DEFAULT_STATUS;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (EXPORT_STATUSES.contains(normalized)) {
            return normalized;
        }
        return status;
    }

    // 删除单个导出任务
    @Override
    @CrestAudit(id = "#p0", ot = LogOT.DELETE, st = LogST.DATA)
    public void delete(String id) {
        exportCenterManage.delete(id);
    }

    // 批量删除导出任务
    @Override
    @CrestAudit(ot = LogOT.DELETE, st = LogST.DATA)
    public void delete(List<String> ids) {
        exportCenterManage.delete(ids);
    }

    // 清空指定类型的导出任务
    @Override
    @CrestAudit(ot = LogOT.CLEAR, st = LogST.DATA)
    public void deleteAll(String type) {
        exportCenterManage.deleteAll(type);
    }

    // 下载导出文件
    @Override
    @CrestAudit(id = "#p0", ot = LogOT.DOWNLOAD, st = LogST.DATA)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void download(String id, String ticket, HttpServletResponse response) throws Exception {
        try {
            exportCenterManage.download(id, ticket, response);
        } catch (CrestException e) {
            writeForbidden(response, e);
        }
    }

    // 将下载鉴权失败写为禁止访问响应
    private void writeForbidden(HttpServletResponse response, CrestException e) throws Exception {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtil.toJSONString(new ResultMessage(e.getCode(), DOWNLOAD_FORBIDDEN_MESSAGE)).toString());
    }

    // 生成导出文件下载地址
    @Override
    @CrestAudit(id = "#p0", ot = LogOT.READ, st = LogST.DATA)
    public ResultMessage generateDownloadUri(String id) throws Exception {
        return ResultMessage.success(exportCenterManage.generateDownloadUri(id));
    }

    // 重试导出任务
    @Override
    @CrestAudit(id = "#p0", ot = LogOT.MODIFY, st = LogST.DATA)
    public void retry(String id) {
        exportCenterManage.retry(id);
    }

    // 查询数据集导出数量上限
    @Override
    public String exportLimit() {
        return String.valueOf(ExportCenterUtils.getExportLimit("dataset"));
    }
}
