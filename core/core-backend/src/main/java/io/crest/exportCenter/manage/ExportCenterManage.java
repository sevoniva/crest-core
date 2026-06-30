package io.crest.exportCenter.manage;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.chart.request.ChartExcelRequest;
import io.crest.api.dataset.dto.DataSetExportRequest;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.export.BaseExportApi;
import io.crest.auth.bo.TokenUserBO;
import io.crest.auth.config.SubstituleLoginConfig;
import io.crest.commons.utils.ExcelWatermarkUtils;
import io.crest.dataset.manage.*;
import io.crest.exception.CrestException;
import io.crest.exportCenter.dao.auto.entity.CoreExportDownloadTask;
import io.crest.exportCenter.dao.auto.entity.CoreExportTask;
import io.crest.exportCenter.dao.auto.mapper.CoreExportDownloadTaskMapper;
import io.crest.exportCenter.dao.auto.mapper.CoreExportTaskMapper;
import io.crest.exportCenter.dao.ext.mapper.ExportTaskExtMapper;
import io.crest.exportCenter.queue.ExportTaskQueueService;
import io.crest.i18n.Translator;
import io.crest.lock.CrestRedisLockService;
import io.crest.model.ExportTaskDTO;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.storage.StorageService;
import io.crest.system.manage.SysParameterManage;
import io.crest.utils.*;
import io.crest.visualization.dao.auto.entity.VisualizationWatermark;
import io.crest.visualization.dao.auto.mapper.VisualizationWatermarkMapper;
import io.crest.visualization.dao.ext.mapper.ExtDataVisualizationMapper;
import io.crest.visualization.server.DataVisualizationServer;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import io.crest.visualization.dto.WatermarkContentDTO;
import io.crest.api.permissions.user.vo.UserFormVO;

import java.io.File;
import java.net.InetAddress;
import java.util.*;

/**
 * 导出中心管理服务，负责导出任务创建、下载授权、任务清理、重试和导出文件水印处理
 */
@Component
@Transactional(rollbackFor = Exception.class)
public class ExportCenterManage implements BaseExportApi {
    /**
     * 导出任务主表访问器
     */
    @Resource
    private CoreExportTaskMapper exportTaskMapper;
    /**
     * 下载票据临时表访问器
     */
    @Resource
    private CoreExportDownloadTaskMapper coreExportDownloadTaskMapper;
    /**
     * 导出任务扩展查询访问器
     */
    @Resource
    private ExportTaskExtMapper exportTaskExtMapper;
    /**
     * 数据集分组服务，用于导出前补齐数据集名称和 SQL 上下文
     */
    @Resource
    private DatasetGroupManage datasetGroupManage;
    /**
     * 数据集 SQL 服务，用于校验数据集是否具备可导出 SQL
     */
    @Resource
    private DatasetSQLManage datasetSQLManage;
    /**
     * 可视化服务，用于解析图表或面板的完整路径
     */
    @Resource
    DataVisualizationServer dataVisualizationServer;
    /**
     * 导出任务实际执行与文件下载服务
     */
    @Resource
    private ExportCenterDownLoadManage exportCenterDownLoadManage;
    @Resource
    private ExportTaskQueueService exportTaskQueueService;
    /**
     * 系统参数服务，用于读取导出文件保留时间等配置
     */
    @Resource
    private SysParameterManage sysParameterManage;
    /**
     * 导出线程池核心线程数配置
     */
    @Value("${crest.export.core.size:10}")
    private int core;
    /**
     * 导出线程池最大线程数配置
     */
    @Value("${crest.export.max.size:10}")
    private int max;

    /**
     * 导出文件根目录
     */
    @Value("${crest.path.exportData:/opt/crest/data/exportData/}")
    private String exportData_path;
    /**
     * 可视化水印配置访问器
     */
    @Resource
    private VisualizationWatermarkMapper watermarkMapper;
    /**
     * 可视化扩展查询访问器，用于水印中补充当前用户信息
     */
    @Resource
    private ExtDataVisualizationMapper visualizationMapper;
    @Resource
    private CrestRedisLockService lockService;
    @Resource
    private Environment environment;
    @Resource
    private StorageService storageService;
    /**
     * 旧版导出文件按原文件名落盘，该时间点之前的任务需要兼容旧路径
     */
    private static final long LEGACY_EXPORT_FILE_TIME = 1730277243491L;
    /**
     * 导出任务列表允许过滤的状态集合
     */
    static private List<String> STATUS = Arrays.asList("SUCCESS", "FAILED", "PENDING", "IN_PROGRESS", "ALL");
    /**
     * 校验一次性下载票据并输出导出文件
     */
    public void download(String id, String ticket, HttpServletResponse response) throws Exception {
        CoreExportTask exportTask = validateDownloadTask(id, ticket);
        exportCenterDownLoadManage.download(exportTask, response);
    }

    /**
     * 删除当前用户自己的单个导出任务和对应文件
     */
    public void delete(String id) {
        CoreExportTask exportTask = getCurrentUserExportTask(id);
        deleteTask(exportTask);
    }

    /**
     * 按状态批量删除当前用户的导出任务
     */
    public void deleteAll(String type) {
        if (!STATUS.contains(type)) {
            CrestException.throwException("无效的状态");
        }
        Long currentUserId = currentUserId();
        QueryWrapper<CoreExportTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId);
        if (!type.equalsIgnoreCase("ALL")) {
            queryWrapper.eq("export_status", type);
        }
        List<CoreExportTask> exportTasks = exportTaskMapper.selectList(queryWrapper);
        exportTasks.parallelStream().forEach(this::deleteTask);

    }

    /**
     * 批量删除指定任务编号列表，仍逐个校验当前用户归属
     */
    public void delete(List<String> ids) {
        ids.forEach(this::delete);
    }

    /**
     * 重试失败的导出任务，并按原任务类型重新进入导出执行流程
     */
    public void retry(String id) {
        CoreExportTask exportTask = getCurrentUserExportTask(id);
        if (!exportTask.getExportStatus().equalsIgnoreCase("FAILED")) {
            CrestException.throwException("正在导出中!");
        }
        exportTask.setExportStatus("PENDING");
        exportTask.setExportProgress("0");
        exportTask.setMsg(null);
        exportTask.setFileSize(null);
        exportTask.setFileSizeUnit(null);
        exportTask.setExportMachineName(hostName());
        exportTask.setExportTime(System.currentTimeMillis());
        exportTaskMapper.updateById(exportTask);
        storageService.deleteDirectory(exportData_path, id);
        if (exportTask.getExportFromType().equalsIgnoreCase("chart")) {
            ChartExcelRequest request = JsonUtil.parseObject(exportTask.getParams(), ChartExcelRequest.class);
            submitExportTask(exportTask, () -> exportCenterDownLoadManage.startViewTask(exportTask, request));
        }
        if (exportTask.getExportFromType().equalsIgnoreCase("dataset")) {
            DataSetExportRequest request = JsonUtil.parseObject(exportTask.getParams(), DataSetExportRequest.class);
            try {
                prepareDatasetExportRequest(exportTask.getExportFrom(), request);
                exportTask.setFileName(request.getFilename() + ".xlsx");
                exportTask.setParams(JsonUtil.toJSONString(request).toString());
                exportTaskMapper.updateById(exportTask);
            } catch (Exception e) {
                exportTask.setMsg(e.getMessage());
                exportTask.setExportStatus("FAILED");
                exportTaskMapper.updateById(exportTask);
                CrestException.throwException(e.getMessage());
            }
            submitExportTask(exportTask, () -> exportCenterDownLoadManage.startDatasetTask(exportTask, request));
        }
    }

    /**
     * 分页查询当前用户导出任务，并补齐资源路径和组织信息
     */
    public IPage<ExportTaskDTO> pager(Page<ExportTaskDTO> page, String status) {
        if (!STATUS.contains(status)) {
            CrestException.throwException("Invalid status: " + status);
        }

        Long currentUserId = currentUserId();
        expireMissingSuccessTasks(currentUserId);
        QueryWrapper<CoreExportTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId);
        if (!status.equalsIgnoreCase("ALL")) {
            queryWrapper.eq("export_status", status);
        }
        queryWrapper.orderByDesc("export_time");
        IPage<ExportTaskDTO> pager = exportTaskExtMapper.pager(page, queryWrapper);

        List<ExportTaskDTO> records = pager.getRecords();
        records.forEach(exportTask -> {
            if (status.equalsIgnoreCase("ALL") || status.equalsIgnoreCase(exportTask.getExportStatus())) {
                setExportFromAbsName(exportTask);
            }
            if (status.equalsIgnoreCase("ALL") || status.equalsIgnoreCase(exportTask.getExportStatus())) {
                proxy().setOrg(exportTask);
            }
        });

        return pager;
    }

    /**
     * 统计当前用户各状态导出任务数量
     */
    public Map<String, Long> exportTasks() {
        Long currentUserId = currentUserId();
        expireMissingSuccessTasks(currentUserId);
        Map<String, Long> result = new HashMap<>();
        QueryWrapper<CoreExportTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId);
        queryWrapper.eq("export_status", "IN_PROGRESS");
        result.put("IN_PROGRESS", exportTaskMapper.selectCount(queryWrapper));

        queryWrapper.clear();
        queryWrapper.eq("user_id", currentUserId);
        queryWrapper.eq("export_status", "SUCCESS");
        result.put("SUCCESS", exportTaskMapper.selectCount(queryWrapper));

        queryWrapper.clear();
        queryWrapper.eq("user_id", currentUserId);
        queryWrapper.eq("export_status", "FAILED");
        result.put("FAILED", exportTaskMapper.selectCount(queryWrapper));

        queryWrapper.clear();
        queryWrapper.eq("user_id", currentUserId);
        queryWrapper.eq("export_status", "PENDING");
        result.put("PENDING", exportTaskMapper.selectCount(queryWrapper));

        queryWrapper.clear();
        queryWrapper.eq("user_id", currentUserId);
        result.put("ALL", exportTaskMapper.selectCount(queryWrapper));
        return result;
    }

    /**
     * 预留的组织信息补全入口，通过代理调用保留事务和扩展点
     */
    public void setOrg(ExportTaskDTO exportTaskDTO) {
    }

    /**
     * 获取当前 Spring 代理实例，避免类内调用绕过代理增强
     */
    private ExportCenterManage proxy() {
        return CommonBeanFactory.getBean(ExportCenterManage.class);
    }

    /**
     * 按导出来源类型补齐资源完整路径名称
     */
    private void setExportFromAbsName(ExportTaskDTO exportTaskDTO) {
        if (exportTaskDTO.getExportFromType().equalsIgnoreCase("chart")) {
            exportTaskDTO.setExportFromName(dataVisualizationServer.getAbsPath(exportTaskDTO.getExportFrom()));
        }
        if (exportTaskDTO.getExportFromType().equalsIgnoreCase("dataset")) {
            List<String> fullName = new ArrayList<>();
            datasetGroupManage.geFullName(Long.valueOf(exportTaskDTO.getExportFrom()), fullName);
            Collections.reverse(fullName);
            List<String> finalFullName = fullName;
            exportTaskDTO.setExportFromName(String.join("/", finalFullName));
        }
    }

    /**
     * 读取当前主机名，导出任务会记录执行机器便于排查
     */
    private String hostName() {
        String hostname = null;
        try {
            InetAddress localMachine = InetAddress.getLocalHost();
            hostname = localMachine.getHostName();
        } catch (Exception e) {
            CrestException.throwException("请设置主机名！");
        }
        return hostname;
    }

    /**
     * 创建图表或可视化资源导出任务，并立即提交到对应执行入口
     */
    public void addTask(String exportFrom, String exportFromType, ChartExcelRequest request, String busiFlag) {
        Long currentUserId = currentUserId();
        CoreExportTask exportTask = new CoreExportTask();
        exportTask.setId(IDUtils.snowID().toString());
        exportTask.setUserId(currentUserId);
        exportTask.setExportFrom(Long.valueOf(exportFrom));
        exportTask.setExportFromType(exportFromType);
        exportTask.setExportStatus("PENDING");
        exportTask.setFileName(request.getViewName() + ".xlsx");
        exportTask.setExportProgress("0");
        exportTask.setExportTime(System.currentTimeMillis());
        exportTask.setParams(JsonUtil.toJSONString(request).toString());
        exportTask.setExportMachineName(hostName());
        exportTaskMapper.insert(exportTask);
        if (busiFlag.equalsIgnoreCase("dashboard")) {
            submitExportTask(exportTask, () -> exportCenterDownLoadManage.startPanelViewTask(exportTask, request));
        } else {
            submitExportTask(exportTask, () -> exportCenterDownLoadManage.startDataVViewTask(exportTask, request));
        }

    }

    /**
     * 创建数据集导出任务，提交前会校验数据集 SQL 是否完整
     */
    public void addTask(Long exportFrom, String exportFromType, DataSetExportRequest request) throws Exception {
        prepareDatasetExportRequest(exportFrom, request);
        Long currentUserId = currentUserId();
        CoreExportTask exportTask = new CoreExportTask();
        exportTask.setId(IDUtils.snowID().toString());
        exportTask.setUserId(currentUserId);
        exportTask.setExportFrom(exportFrom);
        exportTask.setExportFromType(exportFromType);
        exportTask.setExportStatus("PENDING");
        exportTask.setFileName(request.getFilename() + ".xlsx");
        exportTask.setExportProgress("0");
        exportTask.setExportTime(System.currentTimeMillis());
        exportTask.setParams(JsonUtil.toJSONString(request).toString());
        exportTask.setExportMachineName(hostName());
        exportTaskMapper.insert(exportTask);
        submitExportTask(exportTask, () -> exportCenterDownLoadManage.startDatasetTask(exportTask, request));
    }

    /**
     * 补齐数据集导出请求的文件名，并校验数据集能生成导出 SQL
     */
    private void prepareDatasetExportRequest(Long exportFrom, DataSetExportRequest request) throws Exception {
        DatasetGroupInfoDTO dataset = datasetGroupManage.datasetGroupInfoDTO(exportFrom, null);
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(dataset, null);
        if (sqlMap == null || StringUtils.isBlank((String) sqlMap.get("sql"))) {
            CrestException.throwException("数据集配置不完整，无法导出");
        }
        if (StringUtils.isBlank(request.getFilename())) {
            request.setFilename(StringUtils.defaultIfBlank(dataset.getName(), "dataset-" + exportFrom));
        }
    }

    /**
     * 对外部 API 创建导出任务，仅入库等待后续调度处理
     */
    @Override
    public void addTask(String exportFromId, String exportFromType, HashMap<String, Object> request, Long userId, Long org) {
        CoreExportTask exportTask = new CoreExportTask();
        request.put("org", org);
        exportTask.setId(IDUtils.snowID().toString());
        exportTask.setUserId(userId);
        exportTask.setExportFrom(Long.valueOf(exportFromId));
        exportTask.setExportFromType(exportFromType);
        exportTask.setExportStatus("PENDING");
        exportTask.setFileName(request.get("name") + ".xlsx");
        exportTask.setExportProgress("0");
        exportTask.setExportTime(System.currentTimeMillis());
        exportTask.setParams(JsonUtil.toJSONString(request).toString());
        exportTask.setExportMachineName(hostName());
        exportTaskMapper.insert(exportTask);
    }

    private void submitExportTask(CoreExportTask exportTask, Runnable localRunner) {
        if (!exportTaskQueueService.enabled()) {
            localRunner.run();
            return;
        }
        try {
            exportTask.setLastEnqueueTime(System.currentTimeMillis());
            exportTaskMapper.updateById(exportTask);
            exportTaskQueueService.enqueueExportTask(exportTask.getId());
        } catch (Exception e) {
            exportTask.setExportStatus("FAILED");
            exportTask.setExportProgress("100");
            exportTask.setMsg(e.getMessage());
            exportTask.setLastError(e.getMessage());
            exportTaskMapper.updateById(exportTask);
            CrestException.throwException(e.getMessage());
        }
    }

    /**
     * 清理超过系统保留期的导出任务和文件
     */
    public void cleanLog() {
        String key = "basic.exportFileLiveTime";
        String val = sysParameterManage.singleVal(key);
        if (StringUtils.isBlank(val)) {
            CrestException.throwException("未获取到文件保留时间");
        }
        QueryWrapper<CoreExportTask> queryWrapper = new QueryWrapper<>();
        long expTime = Long.parseLong(val) * 24L * 3600L * 1000L;
        long threshold = System.currentTimeMillis() - expTime;
        queryWrapper.lt("export_time", threshold);
        exportTaskMapper.selectList(queryWrapper).forEach(coreExportTask -> {
            deleteTask(coreExportTask);
        });

    }

    /**
     * 按系统默认水印配置给 Excel 工作簿追加水印
     */
    public void addWatermarkTools(Workbook wb) {
        VisualizationWatermark watermark = watermarkMapper.selectById("system_default");
        WatermarkContentDTO watermarkContent = JsonUtil.parseObject(watermark.getSettingContent(), WatermarkContentDTO.class);
        if (watermarkContent.getEnable() && watermarkContent.getExcelEnable()) {
            UserFormVO userInfo = visualizationMapper.queryInnerUserInfo(currentUserId());
            // 水印图片只生成一次，后续复用同一个图片编号写入所有 Sheet
            int watermarkPictureIdx = ExcelWatermarkUtils.addWatermarkImage(wb, watermarkContent, userInfo);
            for (Sheet sheet : wb) {
                // 每个 Sheet 都要显式挂载水印，否则多 Sheet 导出只会首个表生效
                ExcelWatermarkUtils.addWatermarkToSheet(sheet, watermarkPictureIdx);
            }
        }
    }

    /**
     * 为成功任务生成一次性下载地址，并刷新下载票据有效期
     */
    public String generateDownloadUri(String id) {
        CoreExportTask exportTask = getCurrentUserExportTask(id);
        ensureDownloadableTask(exportTask);
        long createTime = System.currentTimeMillis();
        CoreExportDownloadTask coreExportDownloadTask = coreExportDownloadTaskMapper.selectById(id);
        if (coreExportDownloadTask != null) {
            coreExportDownloadTask.setCreateTime(createTime);
            if (coreExportDownloadTask.getValidTime() == null) {
                coreExportDownloadTask.setValidTime(5L);
            }
            coreExportDownloadTaskMapper.updateById(coreExportDownloadTask);
        } else {
            coreExportDownloadTask = new CoreExportDownloadTask();
            coreExportDownloadTask.setId(id);
            coreExportDownloadTask.setCreateTime(createTime);
            coreExportDownloadTask.setValidTime(5L);
            coreExportDownloadTaskMapper.insert(coreExportDownloadTask);
        }
        String ticket = buildDownloadTicket(exportTask, createTime, coreExportDownloadTask.getValidTime());
        return UriComponentsBuilder.newInstance()
                .path("/")
                .pathSegment("export-center", "download", id)
                .queryParam("ticket", ticket)
                .build()
                .encode()
                .toUriString();
    }


    /**
     * 定时清理过期下载票据，下载链接失效后不再允许继续访问文件
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void checkDownLoadInfos() {
        // 下载票据清理归调度角色执行，避免 Worker 副本重复扫描。
        if (environment != null && !CrestRuntimeRole.from(environment).runsScheduler()) {
            return;
        }
        lockService.runWithLock("scheduled:clean-download-tickets", () -> {
            coreExportDownloadTaskMapper.selectList(null).forEach(downLoadInfo -> {
                if (System.currentTimeMillis() - downLoadInfo.getCreateTime() > downLoadInfo.getValidTime() * 60 * 1000) {
                    coreExportDownloadTaskMapper.deleteById(downLoadInfo.getId());
                }
            });
        });
    }

    /**
     * 下载票据展示对象，validTime 单位为分钟
     */
    @Data
    public class DownLoadInfo {
        String id;
        Long validTime;
        Long createTime;
    }

    /**
     * 读取当前用户自己的导出任务，不存在或越权时按不存在处理
     */
    private CoreExportTask getCurrentUserExportTask(String id) {
        Long currentUserId = currentUserId();
        CoreExportTask exportTask = exportTaskMapper.selectById(id);
        if (exportTask == null || !Objects.equals(exportTask.getUserId(), currentUserId)) {
            CrestException.throwException("任务不存在");
        }
        return exportTask;
    }

    /**
     * 删除任务记录和导出目录
     */
    private void deleteTask(CoreExportTask exportTask) {
        if (exportTask == null) {
            return;
        }
        String id = exportTask.getId();
        storageService.deleteDirectory(exportData_path, id);
        exportTaskMapper.deleteById(id);
    }

    /**
     * 校验下载票据、任务归属和有效期，校验通过后票据立即失效
     */
    private CoreExportTask validateDownloadTask(String id, String ticket) {
        if (StringUtils.isBlank(ticket)) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        CoreExportDownloadTask coreExportDownloadTask = coreExportDownloadTaskMapper.selectById(id);
        CoreExportTask exportTask = exportTaskMapper.selectById(id);
        if (exportTask == null) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        DecodedJWT jwt = null;
        try {
            jwt = SignedTokenUtils.verify(ticket, resolveTicketSecret(exportTask));
        } catch (Exception e) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        if (jwt == null) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        String taskId = jwt.getClaim("taskId").asString();
        Long uid = jwt.getClaim("uid").asLong();
        Long ticketTime = jwt.getClaim("ts").asLong();
        if (!Objects.equals(id, taskId) || !Objects.equals(uid, exportTask.getUserId()) || ticketTime == null) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        if (coreExportDownloadTask != null
                && coreExportDownloadTask.getValidTime() != null
                && System.currentTimeMillis() - ticketTime > coreExportDownloadTask.getValidTime() * 60 * 1000) {
            CrestException.throwException(Translator.get("i18n_download_link_invalid"));
        }
        ensureDownloadableTask(exportTask);
        if (coreExportDownloadTask != null) {
            coreExportDownloadTaskMapper.deleteById(id);
        }
        return exportTask;
    }

    /**
     * 将已成功但文件丢失的任务标记为失败，避免列表继续展示可下载状态
     */
    private void expireMissingSuccessTasks(Long userId) {
        QueryWrapper<CoreExportTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("export_status", "SUCCESS");
        exportTaskMapper.selectList(queryWrapper).forEach(exportTask -> {
            if (!exportFileExists(exportTask)) {
                markExportFileMissing(exportTask);
            }
        });
    }

    /**
     * 下载前检查成功任务文件是否存在，缺失时同步更新任务状态
     */
    private void failIfExportFileMissing(CoreExportTask exportTask) {
        if (exportTask != null && "SUCCESS".equalsIgnoreCase(exportTask.getExportStatus()) && !exportFileExists(exportTask)) {
            markExportFileMissing(exportTask);
            CrestException.throwException(Translator.get("i18n_export_file_missing"));
        }
    }

    /**
     * 校验任务是否可下载，失败任务优先返回任务自身错误信息
     */
    private void ensureDownloadableTask(CoreExportTask exportTask) {
        if ("SUCCESS".equalsIgnoreCase(exportTask.getExportStatus())) {
            failIfExportFileMissing(exportTask);
            return;
        }
        if (StringUtils.isNotBlank(exportTask.getMsg())) {
            CrestException.throwException(Translator.get(exportTask.getMsg()));
        }
        CrestException.throwException(Translator.get("i18n_export_task_not_success"));
    }

    /**
     * 将文件缺失的成功任务转为失败，并删除对应下载票据
     */
    private void markExportFileMissing(CoreExportTask exportTask) {
        exportTask.setExportStatus("FAILED");
        exportTask.setExportProgress("100");
        exportTask.setMsg(Translator.get("i18n_export_file_missing"));
        exportTaskMapper.updateById(exportTask);
        coreExportDownloadTaskMapper.deleteById(exportTask.getId());
    }

    /**
     * 判断任务导出文件是否仍存在于文件系统
     */
    private boolean exportFileExists(CoreExportTask exportTask) {
        return storageService.isRegularFile(resolveExportFile(exportTask));
    }

    /**
     * 根据任务时间兼容新旧导出文件命名规则
     */
    private File resolveExportFile(CoreExportTask exportTask) {
        String fileName = exportTask.getExportTime() != null && exportTask.getExportTime() < LEGACY_EXPORT_FILE_TIME
                ? exportTask.getFileName()
                : exportTask.getId() + ".xlsx";
        return storageService.resolve(exportData_path, exportTask.getId(), fileName);
    }

    /**
     * 生成带任务编号、用户编号和创建时间的一次性下载票据
     */
    private String buildDownloadTicket(CoreExportTask exportTask, long createTime, Long validTime) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("taskId", exportTask.getId());
        claims.put("uid", exportTask.getUserId());
        claims.put("ts", createTime);
        return SignedTokenUtils.sign(claims, resolveTicketSecret(exportTask), new Date(createTime + validTime * 60 * 1000));
    }

    /**
     * 根据系统密钥和用户编号派生下载票据密钥，避免不同用户票据互通
     */
    private String resolveTicketSecret(CoreExportTask exportTask) {
        return SubstituleLoginConfig.getTokenSecret() + ":" + exportTask.getUserId();
    }

    /**
     * 读取当前登录用户编号，导出中心所有用户态操作都依赖该值隔离任务
     */
    private Long currentUserId() {
        TokenUserBO user = AuthUtils.getUser();
        if (user == null || user.getUserId() == null) {
            CrestException.throwException("user not found");
        }
        return user.getUserId();
    }
}
