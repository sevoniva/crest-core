package io.crest.datasource.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.api.dataset.dto.PreviewSqlDTO;
import io.crest.api.ds.DatasourceApi;
import io.crest.api.ds.vo.*;
import io.crest.api.permissions.relation.api.RelationApi;
import io.crest.commons.constants.TaskStatus;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.constant.SQLConstants;
import io.crest.dataset.manage.DatasetDataManage;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.*;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.dao.auto.mapper.CoreDsFinishPageMapper;
import io.crest.datasource.dao.auto.mapper.CoreScheduleStateMapper;
import io.crest.datasource.dao.ext.mapper.DataSourceExtMapper;
import io.crest.datasource.dao.ext.mapper.TaskLogExtMapper;
import io.crest.datasource.manage.DataSourceManage;
import io.crest.datasource.manage.DatasourceSyncManage;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.provider.CalciteProvider;
import io.crest.datasource.provider.EngineProvider;
import io.crest.datasource.type.ObOracle;
import io.crest.datasource.provider.ExcelUtils;
import io.crest.datasource.request.EngineRequest;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.i18n.Translator;
import io.crest.job.schedule.CheckDsStatusJob;
import io.crest.job.schedule.ScheduleManager;
import io.crest.log.CrestAudit;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.crest.result.ResultCode;
import io.crest.system.dao.auto.entity.CoreSysSetting;
import io.crest.system.manage.CoreUserManage;
import io.crest.utils.*;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.crest.datasource.server.DatasourceTaskServer.ScheduleType.MANUAL;
import static io.crest.datasource.server.DatasourceTaskServer.ScheduleType.RIGHTNOW;


/**
 * 数据源服务入口，负责数据源目录、连接配置、Excel/API 同步表和状态检查的应用层编排
 * 该类会触发连接探测、引擎表创建、同步任务调度和权限校验，但不直接实现各数据源协议细节
 */
@RestController
@RequestMapping("/datasource")
@SuppressWarnings({"deprecation", "unchecked"})
public class DatasourceServer implements DatasourceApi {
    private static final Pattern ORACLE_RECYCLE_BIN_TABLE_PATTERN = Pattern.compile("^BIN\\$.*\\$[0-9]+$", Pattern.CASE_INSENSITIVE);
    private static final String EXCEL_ROW_ID_FIELD = "crest_row_uuid";
    private static final String EXCEL_EDIT_ROW_ID = "_rowId";
    private static final int MAX_EXCEL_EDIT_PAGE_SIZE = 500;
    private static final int MAX_EXCEL_EDIT_BATCH_SIZE = 5000;
    private static final List<String> EXCEL_UPLOAD_SUFFIXES = List.of("xlsx", "xls", "csv");
    private static final String ALLOWED_DATASOURCE_TYPES_PROPERTY = "crest.datasource.allowed-types";
    private static final Set<String> DEFAULT_ALLOWED_DATASOURCE_TYPES = Set.of("obOracle", "Excel", "ExcelRemote", "API");

    @Resource
    private CoreDatasourceMapper datasourceMapper;
    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasourceTaskServer datasourceTaskServer;
    @Resource
    private CalciteProvider calciteProvider;
    @Resource
    private DatasourceSyncManage datasourceSyncManage;
    @Resource
    private TaskLogExtMapper taskLogExtMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Resource
    private DataSourceManage dataSourceManage;
    @Resource
    private CoreScheduleStateMapper coreScheduleStateMapper;
    @Resource
    private DataSourceExtMapper dataSourceExtMapper;
    @Resource
    private CoreDsFinishPageMapper coreDsFinishPageMapper;
    @Resource
    private DatasetDataManage datasetDataManage;
    @Resource
    private ScheduleManager scheduleManager;
    @Resource
    private CoreUserManage coreUserManage;
    @Autowired(required = false)
    private PluginManageApi pluginManage;
    @Autowired(required = false)
    private RelationApi relationManage;
    @Resource
    private Environment environment;

    /**
     * 解码前端提交的 Base64 字段，统一把格式错误转换为参数异常
     */
    private static String decodeBase64RequestValue(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), fieldName + "格式无效");
            return "";
        }
    }

    /**
     * 解析重复连接校验使用的配置，OB Oracle 需要保留按用户名推导 schema 的逻辑。
     */
    private static DatasourceConfiguration parseRepeatConfiguration(String configuration, String type) {
        DatasourceConfiguration datasourceConfiguration = Strings.CI.equals(type, DatasourceConfiguration.DatasourceType.obOracle.getType())
                ? JsonUtil.parseObject(configuration, ObOracle.class)
                : JsonUtil.parseObject(configuration, DatasourceConfiguration.class);
        if (datasourceConfiguration != null) {
            datasourceConfiguration.convertJdbcUrl();
        }
        return datasourceConfiguration;
    }

    /**
     * 忽略首尾空白比较连接配置字段，避免空值触发 NPE。
     */
    private static boolean sameConfigValue(String left, String right) {
        return Strings.CI.equals(StringUtils.trimToEmpty(left), StringUtils.trimToEmpty(right));
    }

    /**
     * 判断两个连接配置是否指向同一个物理数据源。
     */
    private static boolean isSameConnection(String type, DatasourceConfiguration left, DatasourceConfiguration right) {
        if (left == null || right == null) {
            return false;
        }
        if (!sameConfigValue(left.getHost(), right.getHost()) || !Objects.equals(left.getPort(), right.getPort())) {
            return false;
        }
        if (Strings.CI.equalsAny(type,
                DatasourceConfiguration.DatasourceType.sqlServer.getType(),
                DatasourceConfiguration.DatasourceType.db2.getType(),
                DatasourceConfiguration.DatasourceType.oracle.getType(),
                DatasourceConfiguration.DatasourceType.pg.getType(),
                DatasourceConfiguration.DatasourceType.redshift.getType())) {
            return sameConfigValue(left.getDataBase(), right.getDataBase())
                    && sameConfigValue(left.getSchema(), right.getSchema());
        }
        if (Strings.CI.equals(type, DatasourceConfiguration.DatasourceType.obOracle.getType())) {
            return sameConfigValue(left.getUsername(), right.getUsername())
                    && sameConfigValue(left.getSchema(), right.getSchema());
        }
        if (Strings.CI.equals(type, DatasourceConfiguration.DatasourceType.obMysql.getType())) {
            return sameConfigValue(left.getUsername(), right.getUsername())
                    && sameConfigValue(left.getDataBase(), right.getDataBase());
        }
        return sameConfigValue(left.getDataBase(), right.getDataBase());
    }

    /**
     * Core 首版默认 fail-closed，只保留 OB Oracle、Excel 和 API 数据源。
     */
    private Set<String> allowedDatasourceTypes() {
        if (environment == null) {
            return DEFAULT_ALLOWED_DATASOURCE_TYPES;
        }
        String configured = environment.getProperty(ALLOWED_DATASOURCE_TYPES_PROPERTY, "");
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_ALLOWED_DATASOURCE_TYPES;
        }
        return Arrays.stream(configured.split(","))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private boolean isDatasourceTypeAllowed(String type) {
        if (Strings.CS.equals(type, DatasourceConfiguration.DatasourceType.folder.getType())) {
            return true;
        }
        Set<String> allowedTypes = allowedDatasourceTypes();
        return allowedTypes.contains(type);
    }

    /**
     * 数据同步范围，区分全量抽取和追加抽取两类任务
     */
    public enum UpdateType {
        all_scope, add_scope
    }

    public static final List<String> notFullDs = List.of("folder", "Excel", "API");

    private TypeReference<List<ApiDefinition>> listTypeReference = new TypeReference<List<ApiDefinition>>() {
    };
    @Resource
    private CommonThreadPool commonThreadPool;
    private boolean isUpdatingStatus = false;
    private static List<Long> syncDsIds = new ArrayList<>();


    /**
     * 查询入口暂未承载业务逻辑，保留接口实现以兼容数据源 API 契约
     */
    @Override
    public List<DatasourceDTO> query(String keyWord) {
        return null;
    }

    /**
     * 检查同类型连接型数据源是否重复，目录、Excel 和 API 类型不参与重复连接判断
     */
    @Override
    public boolean checkRepeat(@RequestBody BusiDsRequest dataSourceDTO) {
        if (Arrays.asList("folder", "es").contains(dataSourceDTO.getType()) || dataSourceDTO.getType().contains("API") || dataSourceDTO.getType().contains("Excel")) {
            return false;
        }
        BusiNodeRequest request = new BusiNodeRequest();
        request.setBusiFlag("datasource");
        List<BusiNodeVO> busiNodeVOS = dataSourceManage.tree(request);
        List<Long> ids = new ArrayList<>();
        filterDs(busiNodeVOS, ids, dataSourceDTO.getType(), dataSourceDTO.getId());

        if (CollectionUtils.isEmpty(ids)) {
            return false;
        }
        QueryWrapper<CoreDatasource> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);

        List<CoreDatasource> datasources = datasourceMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(datasources)) {
            return false;
        }
        dataSourceDTO.setConfiguration(decodeBase64RequestValue(dataSourceDTO.getConfiguration(), "数据源配置"));
        DatasourceConfiguration configuration = parseRepeatConfiguration(dataSourceDTO.getConfiguration(), dataSourceDTO.getType());
        for (CoreDatasource datasource : datasources) {
            if (notFullDs.stream().anyMatch(e -> datasource.getType().contains(e))) {
                continue;
            }
            if (StringUtils.isEmpty(datasource.getConfiguration())) {
                continue;
            }
            DatasourceConfiguration compare = parseRepeatConfiguration(datasource.getConfiguration(), datasource.getType());
            if (isSameConnection(dataSourceDTO.getType(), configuration, compare)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移动数据源或目录到新的父节点，并校验目标目录不会形成循环引用
     */
    @CrestAudit(id = "#p0.id", ot = LogOT.MODIFY, st = LogST.DATASOURCE)
    @Override
    @Transactional
    public DatasourceDTO move(BusiCreateFolderRequest busiDsRequest) {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        if (dataSourceDTO.getPid() == null) {
            CrestException.throwException("目录必选！");
        }
        if (Objects.equals(dataSourceDTO.getId(), dataSourceDTO.getPid())) {
            CrestException.throwException(Translator.get("i18n_pid_not_eq_id"));
        }
        requireDatasourceAccess(dataSourceDTO.getId());
        if (dataSourceDTO.getPid() != 0) {
            requireDatasourceAccess(dataSourceDTO.getPid());
            List<Long> pidList = dataSourceManage.getPidList(dataSourceDTO.getPid());
            if (pidList.contains(dataSourceDTO.getId())) {
                CrestException.throwException(Translator.get("i18n_pid_not_eq_id"));
            }
        }
        dataSourceManage.move(dataSourceDTO);
        return dataSourceDTO;
    }

    /**
     * 重命名数据源或目录，名称唯一性由数据源管理层按同级目录规则校验
     */
    @Override
    @Transactional
    public DatasourceDTO reName(BusiRenameRequest busiDsRequest) {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        if (StringUtils.isEmpty(dataSourceDTO.getName())) {
            CrestException.throwException("名称不能为空！");
        }
        requireDatasourceAccess(dataSourceDTO.getId());
        CoreDatasource datasource = dataSourceManage.datasource(dataSourceDTO.getId());
        datasource.setName(dataSourceDTO.getName());
        dataSourceDTO.setPid(datasource.getPid());
        dataSourceManage.checkName(dataSourceDTO);
        dataSourceManage.innerEditName(datasource);
        return dataSourceDTO;
    }

    /**
     * 创建数据源目录节点。目录没有连接配置，只维护树形结构和审计信息
     */
    @CrestAudit(id = "#p0.id", pid = "#p0.pid", ot = LogOT.CREATE, st = LogST.DATASOURCE)
    @Override
    @Transactional
    public DatasourceDTO createFolder(BusiCreateFolderRequest busiDsRequest) {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        if (ObjectUtils.isNotEmpty(dataSourceDTO.getPid()) && !Objects.equals(dataSourceDTO.getPid(), 0L)) {
            requireDatasourceAccess(dataSourceDTO.getPid());
        }
        dataSourceDTO.setCreateTime(System.currentTimeMillis());
        dataSourceDTO.setUpdateTime(System.currentTimeMillis());
        dataSourceDTO.setType(dataSourceDTO.getNodeType());
        dataSourceDTO.setId(IDUtils.snowID());
        dataSourceDTO.setConfiguration("");
        dataSourceManage.innerSave(dataSourceDTO);
        return dataSourceDTO;
    }

    /**
     * 创建数据源并完成类型特定的初始化：连接型数据源更新连接池，Excel/API 创建引擎表和同步任务
     */
    @CrestAudit(id = "#p0.id", pid = "#p0.pid", ot = LogOT.CREATE, st = LogST.DATASOURCE)
    @Transactional
    @Override
    public DatasourceDTO save(BusiDsRequest busiDsRequest) throws CrestException {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        if (dataSourceDTO.getId() != null && dataSourceDTO.getId() > 0) {
            return update(busiDsRequest);
        }
        if (StringUtils.isNotEmpty(dataSourceDTO.getConfiguration())) {
            dataSourceDTO.setConfiguration(decodeBase64RequestValue(dataSourceDTO.getConfiguration(), "数据源配置"));
        }
        if (ObjectUtils.isNotEmpty(dataSourceDTO.getPid()) && !Objects.equals(dataSourceDTO.getPid(), 0L)) {
            requireDatasourceAccess(dataSourceDTO.getPid());
        }
        preCheckDs(dataSourceDTO);
        dataSourceDTO.setId(IDUtils.snowID());
        dataSourceDTO.setCreateTime(System.currentTimeMillis());
        dataSourceDTO.setUpdateTime(System.currentTimeMillis());
        try {
            checkDatasourceStatus(dataSourceDTO);
        } catch (Exception ignore) {
            dataSourceDTO.setStatus("Error");
        }
        dataSourceDTO.setTaskStatus(TaskStatus.WaitingForExecution.name());
        dataSourceDTO.setCreateBy(AuthUtils.getUser().getUserId().toString());
        dataSourceDTO.setUpdateBy(AuthUtils.getUser().getUserId());

        CoreDatasource coreDatasource = new CoreDatasource();
        BeanUtils.copyBean(coreDatasource, dataSourceDTO);
        dataSourceManage.innerSave(dataSourceDTO);

        if (!dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name())
                && !dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.Excel.name())) {
            calciteProvider.update(dataSourceDTO);
        }

        if (dataSourceDTO.getType().equals(DatasourceConfiguration.DatasourceType.Excel.name())) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(dataSourceDTO);
            List<DatasetTableDTO> tables = ExcelUtils.tables(datasourceRequest);
            for (DatasetTableDTO table : tables) {
                datasourceRequest.setTable(table.getTableName());
                List<TableField> tableFields = ExcelUtils.getTableFields(datasourceRequest);
                try {
                    datasourceSyncManage.createEngineTable(datasourceRequest.getTable(), tableFields);
                } catch (Exception e) {
                    if (e.getMessage().toLowerCase().contains("Row size too large".toLowerCase())) {
                        CrestException.throwException("文本内容超出最大支持范围： " + datasourceRequest.getTable() + ", " + e.getMessage());
                    } else {
                        CrestException.throwException("Failed to create table " + datasourceRequest.getTable() + ", " + e.getMessage());
                    }
                }
            }
            datasourceSyncManage.extractExcelData(coreDatasource, "all_scope");
        } else if (dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(dataSourceDTO);
            List<DatasetTableDTO> tables = (List<DatasetTableDTO>) invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
            checkName(tables.stream().map(DatasetTableDTO::getName).collect(Collectors.toList()));
            for (DatasetTableDTO api : tables) {
                datasourceRequest.setTable(api.getTableName());
                List<TableField> tableFields = (List<TableField>) invokeMethod(coreDatasource.getType(), "getTableFields", DatasourceRequest.class, datasourceRequest);
                try {
                    datasourceSyncManage.createEngineTable(datasourceRequest.getTable(), tableFields);
                } catch (Exception e) {
                    CrestException.throwException("Failed to create table " + datasourceRequest.getTable() + ": " + e.getMessage());
                }
            }

            CoreDatasourceTask coreDatasourceTask = new CoreDatasourceTask();
            BeanUtils.copyBean(coreDatasourceTask, dataSourceDTO.getSyncSetting());
            coreDatasourceTask.setName(coreDatasource.getName() + "-task");
            coreDatasourceTask.setDsId(coreDatasource.getId());
            if (coreDatasourceTask.getStartTime() == null) {
                coreDatasourceTask.setStartTime(System.currentTimeMillis() - 20 * 1000);
            }
            if (Strings.CI.equals(coreDatasourceTask.getSyncRate(), RIGHTNOW.toString())) {
                coreDatasourceTask.setCron(null);
            } else {
                if (coreDatasourceTask.getEndTime() != null && coreDatasourceTask.getEndTime() > 0 && coreDatasourceTask.getStartTime() > coreDatasourceTask.getEndTime()) {
                    CrestException.throwException("结束时间不能小于开始时间！");
                }
            }
            coreDatasourceTask.setTaskStatus(TaskStatus.WaitingForExecution.name());
            datasourceTaskServer.insert(coreDatasourceTask);
            datasourceSyncManage.addSchedule(coreDatasourceTask);
        } else if (dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.ExcelRemote.name())) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(dataSourceDTO);
            List<DatasetTableDTO> tables = ExcelUtils.tables(datasourceRequest);
            for (DatasetTableDTO table : tables) {
                datasourceRequest.setTable(table.getTableName());
                List<TableField> tableFields = ExcelUtils.getTableFields(datasourceRequest);
                try {
                    datasourceSyncManage.createEngineTable(datasourceRequest.getTable(), tableFields);
                } catch (Exception e) {
                    if (e.getMessage().toLowerCase().contains("Row size too large".toLowerCase())) {
                        CrestException.throwException("文本内容超出最大支持范围： " + datasourceRequest.getTable() + ", " + e.getMessage());
                    } else {
                        CrestException.throwException("Failed to create table " + datasourceRequest.getTable() + ", " + e.getMessage());
                    }
                }
            }
            CoreDatasourceTask coreDatasourceTask = new CoreDatasourceTask();
            BeanUtils.copyBean(coreDatasourceTask, dataSourceDTO.getSyncSetting());
            coreDatasourceTask.setName(coreDatasource.getName() + "-task");
            coreDatasourceTask.setDsId(coreDatasource.getId());
            if (coreDatasourceTask.getStartTime() == null) {
                coreDatasourceTask.setStartTime(System.currentTimeMillis() - 20 * 1000);
            }
            if (Strings.CI.equals(coreDatasourceTask.getSyncRate(), RIGHTNOW.toString())) {
                coreDatasourceTask.setCron(null);
            } else {
                if (coreDatasourceTask.getEndTime() != null && coreDatasourceTask.getEndTime() > 0 && coreDatasourceTask.getStartTime() > coreDatasourceTask.getEndTime()) {
                    CrestException.throwException("结束时间不能小于开始时间！");
                }
            }
            coreDatasourceTask.setTaskStatus(TaskStatus.WaitingForExecution.name());
            datasourceTaskServer.insert(coreDatasourceTask);
            datasourceSyncManage.addSchedule(coreDatasourceTask);
        } else {
            checkParams(dataSourceDTO.getConfiguration());
            calciteProvider.update(dataSourceDTO);
        }
        return dataSourceDTO;
    }

    /**
     * 更新数据源配置，并根据表结构变化创建或删除对应的引擎表和同步调度
     */
    @CrestAudit(id = "#p0.id", ot = LogOT.MODIFY, st = LogST.DATASOURCE)
    @Transactional
    @Override
    public DatasourceDTO update(BusiDsRequest busiDsRequest) throws CrestException {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        Long pk = null;
        if (ObjectUtils.isEmpty(pk = dataSourceDTO.getId())) {
            return save(busiDsRequest);
        }
        DatasourceDTO sourceData = dataSourceManage.getDs(pk);
        requireDatasourceAccess(pk);
        dataSourceDTO.setConfiguration(decodeBase64RequestValue(dataSourceDTO.getConfiguration(), "数据源配置"));
        dataSourceDTO.setPid(sourceData.getPid());
        preCheckDs(dataSourceDTO);

        dataSourceDTO.setUpdateTime(System.currentTimeMillis());
        dataSourceDTO.setUpdateBy(AuthUtils.getUser().getUserId());
        try {
            checkDatasourceStatus(dataSourceDTO);
        } catch (Exception e) {
            dataSourceDTO.setStatus("Error");
        }

        CoreDatasource requestDatasource = new CoreDatasource();
        BeanUtils.copyBean(requestDatasource, dataSourceDTO);

        DatasourceRequest sourceTableRequest = new DatasourceRequest();
        sourceTableRequest.setDatasource(sourceData);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(dataSourceDTO);
        List<String> toCreateTables = new ArrayList<>();
        List<String> toDeleteTables = new ArrayList<>();
        if (dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) || dataSourceDTO.getType().equals(DatasourceConfiguration.DatasourceType.ExcelRemote.name())) {
            requestDatasource.setEnableDataFill(null);
            List<DatasetTableDTO> sourceTableDTOs = dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) ? (List<DatasetTableDTO>) invokeMethod(sourceData.getType(), "getApiTables", DatasourceRequest.class, sourceTableRequest) : ExcelUtils.tables(sourceTableRequest);
            List<String> sourceTables = sourceTableDTOs.stream().map(DatasetTableDTO::getTableName).toList();
            List<DatasetTableDTO> datasetTableDTOS = dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) ? (List<DatasetTableDTO>) invokeMethod(sourceData.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest) : ExcelUtils.tables(datasourceRequest);
            List<String> tables = datasetTableDTOS.stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList());


            checkName(datasetTableDTOS.stream().map(DatasetTableDTO::getName).collect(Collectors.toList()));
            toCreateTables = tables.stream().filter(table -> !sourceTables.contains(table)).collect(Collectors.toList());
            toDeleteTables = sourceTables.stream().filter(table -> !tables.contains(table)).collect(Collectors.toList());
            for (String table : tables) {
                for (String sourceTable : sourceTables) {
                    if (table.equals(sourceTable)) {
                        datasourceRequest.setTable(table);
                        List<TableField> tableFieldList = dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) ? (List<TableField>) invokeMethod(datasourceRequest.getDatasource().getType(), "getTableFields", DatasourceRequest.class, datasourceRequest) : ExcelUtils.getTableFields(datasourceRequest);
                        List<String> tableFields = tableFieldList.stream().map(TableField::getName).sorted().collect(Collectors.toList());
                        sourceTableRequest.setTable(sourceTable);
                        List<TableField> sourceTableFieldList = dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) ? (List<TableField>) invokeMethod(sourceTableRequest.getDatasource().getType(), "getTableFields", DatasourceRequest.class, sourceTableRequest) : ExcelUtils.getTableFields(sourceTableRequest);
                        List<String> sourceTableFields = sourceTableFieldList.stream().map(TableField::getName).sorted().collect(Collectors.toList());
                        if (!String.join(",", tableFields).equals(String.join(",", sourceTableFields))) {
                            toDeleteTables.add(table);
                            toCreateTables.add(table);
                        }
                    }
                }
            }
            CoreDatasourceTask coreDatasourceTask = new CoreDatasourceTask();
            BeanUtils.copyBean(coreDatasourceTask, dataSourceDTO.getSyncSetting());
            coreDatasourceTask.setName(requestDatasource.getName() + "-task");
            coreDatasourceTask.setDsId(requestDatasource.getId());
            if (Strings.CI.equals(coreDatasourceTask.getSyncRate(), RIGHTNOW.toString())) {
                coreDatasourceTask.setStartTime(System.currentTimeMillis() - 20 * 1000);
                coreDatasourceTask.setCron(null);
            } else {
                if (coreDatasourceTask.getEndTime() != null && coreDatasourceTask.getEndTime() > 0 && coreDatasourceTask.getStartTime() > coreDatasourceTask.getEndTime()) {
                    CrestException.throwException("结束时间不能小于开始时间！");
                }
            }
            coreDatasourceTask.setTaskStatus(TaskStatus.WaitingForExecution.toString());
            datasourceTaskServer.update(coreDatasourceTask);
            for (String deleteTable : toDeleteTables) {
                try {
                    datasourceSyncManage.dropEngineTable(deleteTable);
                } catch (Exception e) {
                    CrestException.throwException("Failed to drop table " + deleteTable);
                }
            }
            for (String toCreateTable : toCreateTables) {
                datasourceRequest.setTable(toCreateTable);
                try {
                    datasourceSyncManage.createEngineTable(toCreateTable, dataSourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) ? (List<TableField>) invokeMethod(sourceTableRequest.getDatasource().getType(), "getTableFields", DatasourceRequest.class, datasourceRequest) : ExcelUtils.getTableFields(datasourceRequest));
                } catch (Exception e) {
                    CrestException.throwException("Failed to create table " + toCreateTable + ", " + e.getMessage());
                }
            }
            datasourceSyncManage.deleteSchedule(datasourceTaskServer.selectByDSId(dataSourceDTO.getId()));
            datasourceSyncManage.addSchedule(coreDatasourceTask);
            dataSourceManage.checkName(dataSourceDTO);
            dataSourceManage.innerEdit(requestDatasource);
        } else if (dataSourceDTO.getType().equals(DatasourceConfiguration.DatasourceType.Excel.name())) {
            requestDatasource.setEnableDataFill(null);
            List<String> sourceTables = ExcelUtils.tables(sourceTableRequest).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList());
            List<String> tables = ExcelUtils.tables(datasourceRequest).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList());
            if (Objects.equals(dataSourceDTO.getEditType(), replace)) {
                toCreateTables = tables;
                toDeleteTables = sourceTables.stream().filter(s -> tables.contains(s)).collect(Collectors.toList());
                for (String deleteTable : toDeleteTables) {
                    try {
                        datasourceSyncManage.dropEngineTable(deleteTable);
                    } catch (Exception ignore) {
                    }
                }
                for (String toCreateTable : toCreateTables) {
                    datasourceRequest.setTable(toCreateTable);
                    try {
                        datasourceSyncManage.createEngineTable(toCreateTable, ExcelUtils.getTableFields(datasourceRequest));
                    } catch (Exception e) {
                        CrestException.throwException("Failed to create table " + toCreateTable + ", " + e.getMessage());
                    }
                }
                datasourceSyncManage.extractExcelData(requestDatasource, "all_scope");
                dataSourceManage.checkName(dataSourceDTO);
                ExcelUtils.mergeSheets(requestDatasource, sourceData);
                dataSourceManage.innerEdit(requestDatasource);
            } else {
                datasourceSyncManage.extractExcelData(requestDatasource, "add_scope");
                ExcelUtils.mergeSheets(requestDatasource, sourceData);
                dataSourceManage.checkName(dataSourceDTO);
                dataSourceManage.innerEdit(requestDatasource);
            }
        } else {
            checkParams(dataSourceDTO.getConfiguration());
            dataSourceManage.checkName(dataSourceDTO);
            dataSourceManage.innerEdit(requestDatasource);
            calciteProvider.update(dataSourceDTO);
        }
        return dataSourceDTO;
    }


    /**
     * 返回系统内置的数据源类型枚举，插件数据源由保存前校验阶段补充识别
     */
    @Override
    public List<DatasourceConfiguration.DatasourceType> datasourceTypes() {
        return Arrays.stream(DatasourceConfiguration.DatasourceType.values())
                .filter(type -> isDatasourceTypeAllowed(type.getType()))
                .toList();
    }

    /**
     * 校验未持久化的数据源连接配置，返回连接状态而不创建数据源记录
     */
    @Override
    public DatasourceDTO validate(BusiDsRequest busiDsRequest) throws CrestException {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        dataSourceDTO.setConfiguration(decodeBase64RequestValue(dataSourceDTO.getConfiguration(), "数据源配置"));
        preCheckDs(dataSourceDTO);
        CoreDatasource coreDatasource = new CoreDatasource();
        BeanUtils.copyBean(coreDatasource, dataSourceDTO);
        checkDatasourceConfigurationComplete(dataSourceDTO);
        checkDatasourceStatus(dataSourceDTO);
        DatasourceDTO result = new DatasourceDTO();
        result.setType(dataSourceDTO.getType());
        result.setStatus(dataSourceDTO.getStatus());
        return result;
    }

    /**
     * 根据未持久化的数据源配置读取 schema 列表，用于创建表单中的 schema 选择
     */
    @Override
    public List<String> getSchema(BusiDsRequest busiDsRequest) throws CrestException {
        DatasourceDTO dataSourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(dataSourceDTO, busiDsRequest);
        dataSourceDTO.setConfiguration(decodeBase64RequestValue(dataSourceDTO.getConfiguration(), "数据源配置"));
        preCheckDs(dataSourceDTO);
        CoreDatasource coreDatasource = new CoreDatasource();
        BeanUtils.copyBean(coreDatasource, dataSourceDTO);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(dataSourceDTO);
        Provider provider = ProviderFactory.getProvider(dataSourceDTO.getType());
        return provider.getSchema(datasourceRequest);
    }

    /**
     * 读取数据源详情并隐藏敏感凭证，供前端编辑和展示场景使用
     */
    @Override
    public DatasourceDTO hidePw(Long datasourceId) throws CrestException {
        return datasourceDTOById(datasourceId, true);
    }

    /**
     * 返回不含连接配置的简化数据源信息，避免列表或引用场景泄露凭证
     */
    @Override
    public DatasourceDTO getSimpleDs(Long datasourceId) throws CrestException {
        CoreDatasource datasource = requireDatasourceAccess(datasourceId);
        if (datasource == null) {
            CrestException.throwException(Translator.get("i18n_datasource_not_exists"));
        }
        if (datasource.getType().contains("API")) {
            datasource.setConfiguration("[]");
        } else {
            datasource.setConfiguration("");
        }
        datasource.setConfiguration("");
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, datasource);
        return datasourceDTO;
    }

    /**
     * 返回完整数据源详情，调用前会校验当前用户是否拥有创建者权限
     */
    @Override
    public DatasourceDTO get(Long datasourceId) throws CrestException {
        return datasourceDTOById(datasourceId, false);
    }

    /**
     * 内部调用的数据源详情读取入口，保持与公开详情接口一致的权限边界
     */
    @Override
    public DatasourceDTO innerGet(Long datasourceId) throws CrestException {
        return datasourceDTOById(datasourceId, false);
    }

    /**
     * 读取数据源名称，供其他模块展示引用名称时使用
     */
    @Override
    public String getName(Long datasourceId) throws CrestException {
        CoreDatasource datasource = dataSourceManage.getCoreDatasource(datasourceId);
        if (datasource == null) {
            CrestException.throwException(Translator.get("i18n_datasource_not_exists"));
        }
        return datasource.getName();
    }

    /**
     * 按数据源 ID 和类型批量读取内部数据源信息，并补齐 API 数据源的整体状态
     */
    @Override
    public List<DatasourceDTO> innerList(List<Long> ids, List<String> types) throws CrestException {
        List<DatasourceDTO> list = new ArrayList<>();
        LambdaQueryWrapper<CoreDatasource> queryWrapper = new LambdaQueryWrapper<>();
        if (ids != null) {
            if (ids.isEmpty()) {
                return list;
            } else {
                queryWrapper.in(CoreDatasource::getId, ids);
            }
        }
        if (types != null) {
            if (types.isEmpty()) {
                return list;
            } else {
                queryWrapper.in(CoreDatasource::getType, types);
            }
        }
        List<CoreDatasource> dsList = datasourceMapper.selectList(queryWrapper);

        for (CoreDatasource datasource : dsList) {
            DatasourceDTO datasourceDTO = new DatasourceDTO();
            BeanUtils.copyBean(datasourceDTO, datasource);

            if (datasourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.toString())) {
                List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceDTO.getConfiguration(), listTypeReference);
                int success = 0;
                for (ApiDefinition apiDefinition : apiDefinitionList) {
                    String status = null;
                    if (StringUtils.isNotEmpty(datasourceDTO.getStatus())) {
                        JsonNode jsonNode = null;
                        try {
                            jsonNode = objectMapper.readTree(datasourceDTO.getStatus());
                            for (JsonNode node : jsonNode) {
                                if (node.get("name").asText().equals(apiDefinition.getName())) {
                                    status = node.get("status").asText();
                                }
                            }
                            apiDefinition.setStatus(status);
                        } catch (Exception ignore) {
                        }
                    }
                    if (StringUtils.isNotEmpty(status) && status.equalsIgnoreCase("Success")) {
                        success++;
                    }
                }
                if (success == apiDefinitionList.size()) {
                    datasourceDTO.setStatus("Success");
                } else {
                    if (success > 0 && success < apiDefinitionList.size()) {
                        datasourceDTO.setStatus("Warning");
                    } else {
                        datasourceDTO.setStatus("Error");
                    }
                }
            }

            list.add(datasourceDTO);
        }
        return list;
    }

    /**
     * 判断数据源是否被资源血缘引用，删除前用它提示用户存在依赖
     */
    @Override
    public boolean perDelete(Long id) {
        if (relationManage != null) {
            Long count = relationManage.getDsResource(id);
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除数据源或目录。目录删除会递归清理子节点，Excel/API 会同步删除引擎表和任务配置
     */
    @Transactional
    @CrestAudit(id = "#p0", ot = LogOT.DELETE, st = LogST.DATASOURCE)
    @Override
    public void delete(Long datasourceId) throws CrestException {
        requireDatasourceAccess(datasourceId);
        Objects.requireNonNull(io.crest.utils.CommonBeanFactory.getBean(DatasourceServer.class)).recursionDel(datasourceId);
    }

    /**
     * 执行递归删除的主体逻辑，删除顺序保证派生引擎表和子节点不会留下孤儿数据
     */
    public void recursionDel(Long datasourceId) throws CrestException {
        CoreDatasource coreDatasource = dataSourceManage.datasource(datasourceId);
        if (ObjectUtils.isEmpty(coreDatasource)) {
            return;
        }
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, coreDatasource);
        if (coreDatasource.getType().equals(DatasourceConfiguration.DatasourceType.Excel.name())) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(datasourceDTO);
            List<DatasetTableDTO> tables = ExcelUtils.tables(datasourceRequest);
            for (DatasetTableDTO table : tables) {
                datasourceRequest.setTable(table.getTableName());
                try {
                    datasourceSyncManage.dropEngineTable(datasourceRequest.getTable());
                } catch (Exception e) {
                    CrestException.throwException("Failed to drop table " + datasourceRequest.getTable());
                }
            }
        }
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(datasourceDTO);
            List<DatasetTableDTO> tables = (List<DatasetTableDTO>) invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
            for (DatasetTableDTO api : tables) {
                datasourceRequest.setTable(api.getTableName());
                try {
                    datasourceSyncManage.dropEngineTable(datasourceRequest.getTable());
                } catch (Exception e) {
                    CrestException.throwException("Failed to drop table " + datasourceRequest.getTable());
                }

            }

            datasourceTaskServer.deleteByDSId(datasourceId);
        }
        datasourceMapper.deleteById(datasourceId);
        if (notFullDs.stream().allMatch(e -> !coreDatasource.getType().contains(e))) {
            calciteProvider.delete(coreDatasource);
        }

        if (coreDatasource.getType().equals(DatasourceConfiguration.DatasourceType.folder.name())) {
            QueryWrapper<CoreDatasource> wrapper = new QueryWrapper<>();
            wrapper.eq("pid", datasourceId);
            List<CoreDatasource> coreDatasources = datasourceMapper.selectList(wrapper);
            if (ObjectUtils.isNotEmpty(coreDatasources)) {
                for (CoreDatasource record : coreDatasources) {
                    delete(record.getId());
                }
            }
        }
    }


    /**
     * 重新校验已存在数据源的连接状态，并返回脱敏后的状态结果
     */
    @Override
    public DatasourceDTO validate(Long datasourceId) throws CrestException {
        CoreDatasource sourceDatasource = dataSourceManage.getCoreDatasource(datasourceId);
        if (sourceDatasource == null) {
            CrestException.throwException("数据源不存在！");
        }
        CoreDatasource coreDatasource = new CoreDatasource();
        BeanUtils.copyBean(coreDatasource, sourceDatasource);
        return validate(coreDatasource);
    }

    /**
     * 根据系统设置注册数据源状态检查任务，默认按分钟级周期执行
     */
    public void addJob(List<CoreSysSetting> sysSettings) {
        String type = "minute";
        String interval = "30";
        for (CoreSysSetting sysSetting : sysSettings) {
            if (sysSetting.getPkey().equalsIgnoreCase("basic.dsExecuteTime")) {
                type = sysSetting.getPval();
            }
            if (sysSetting.getPkey().equalsIgnoreCase("basic.dsIntervalTime")) {
                interval = sysSetting.getPval();
            }
        }
        String cron = "";
        switch (type) {
            case "hour":
                cron = "0 0 0/hour *  * ? *".replace("hour", interval.toString());
                break;
            default:
                cron = "0 0/minute * *  * ? *".replace("minute", interval.toString());
        }
        scheduleManager.addOrUpdateCronJob(new JobKey("Datasource", "check_status"),
                new TriggerKey("Datasource", "check_status"),
                CheckDsStatusJob.class,
                cron, new Date(System.currentTimeMillis()), null, new JobDataMap());
    }

    /**
     * 读取数据源树，树节点权限和过滤规则委托给数据源管理层处理
     */
    @CrestAudit(ot = LogOT.READ, st = LogST.DATASOURCE)
    @Override
    public List<BusiNodeVO> tree(BusiNodeRequest request) throws CrestException {
        return dataSourceManage.tree(request);
    }

    /**
     * 返回指定数据源可选择的数据表。API、Excel 和连接型数据源分别走各自的表发现逻辑
     */
    @Override
    public List<DatasetTableDTO> tables(DatasetTableDTO datasetTableDTO) throws CrestException {
        CoreDatasource coreDatasource = requireDatasourceAccess(datasetTableDTO.getDatasourceId());
        if (coreDatasource == null) {
            CrestException.throwException("无效数据源！");
        }
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, coreDatasource);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(datasourceDTO);
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
            List<DatasetTableDTO> datasetTableDTOS = (List<DatasetTableDTO>) invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
            return datasetTableDTOS;
        }
        if (coreDatasource.getType().contains("Excel")) {
            return ExcelUtils.tables(datasourceRequest);
        }
        Provider provider = ProviderFactory.getProvider(datasourceDTO.getType());
        List<DatasetTableDTO> tables = provider.tables(datasourceRequest);
        if (StringUtils.endsWithIgnoreCase(coreDatasource.getType(), DatasourceConfiguration.DatasourceType.oracle.name())) {
            return tables.stream().filter(table -> !isOracleRecycleBinTable(table)).collect(Collectors.toList());
        }
        return tables;
    }

    /**
     * 判断 Oracle 表描述是否属于回收站表，避免系统临时表出现在业务选择列表中
     */
    private boolean isOracleRecycleBinTable(DatasetTableDTO table) {
        if (table == null) {
            return false;
        }
        return isOracleRecycleBinName(table.getTableName()) || isOracleRecycleBinName(table.getName());
    }

    /**
     * 按 Oracle 回收站命名规则判断表名，兼容带双引号的元数据返回值
     */
    private boolean isOracleRecycleBinName(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return false;
        }
        String normalized = StringUtils.removeEnd(StringUtils.removeStart(tableName.trim(), "\""), "\"");
        return ORACLE_RECYCLE_BIN_TABLE_PATTERN.matcher(normalized).matches();
    }

    /**
     * 返回 API 或远程 Excel 数据表的最近同步状态，状态来自同步任务日志
     */
    @Override
    public List<DatasetTableDTO> tableStatus(DatasetTableDTO datasetTableDTO) throws CrestException {
        CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(datasetTableDTO.getDatasourceId());
        if (coreDatasource == null) {
            CrestException.throwException("无效数据源！");
        }
        List<DatasetTableDTO> datasetTableDTOS = new ArrayList<>();
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, coreDatasource);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(datasourceDTO);
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name())) {
            datasetTableDTOS = (List<DatasetTableDTO>) invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
        }
        if (coreDatasource.getType().equalsIgnoreCase(DatasourceConfiguration.DatasourceType.ExcelRemote.name())) {
            datasetTableDTOS = ExcelUtils.tables(datasourceRequest);
        }
        datasetTableDTOS.forEach(datasetTableDTO1 -> {
            CoreDatasourceTaskLog log = datasourceTaskServer.lastSyncLogForTable(datasetTableDTO.getDatasourceId(), datasetTableDTO1.getTableName());
            if (log != null) {
                datasetTableDTO1.setLastUpdateTime(log.getStartTime());
                datasetTableDTO1.setStatus(log.getTaskStatus());
            }
        });
        return datasetTableDTOS;
    }

    /**
     * 获取指定表字段。Excel/API 同步表会从引擎表或保存配置中读取，并过滤系统行标识字段
     */
    @Override
    public List<TableField> getTableField(Map<String, String> req) throws CrestException {
        String tableName = req.get("tableName");
        String datasourceId = req.get("datasourceId");
        DatasetTableDTO datasetTableDTO = new DatasetTableDTO();
        datasetTableDTO.setDatasourceId(Long.valueOf(datasourceId));
        if (!tables(datasetTableDTO).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList()).contains(tableName)) {
            CrestException.throwException("无效的表名！");
        }
        CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(Long.parseLong(datasourceId));
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) || coreDatasource.getType().contains("Excel")) {
            if (coreDatasource.getType().contains("Excel")) {
                datasourceRequest.setTable(tableName);
                return checkedExcelFields(ExcelUtils.getTableFields(datasourceRequest));
            }
            datasourceRequest.setDatasource(transDTO(engineManage.getEngineDatasource()));
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, engineManage.getEngineDatasource());
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            datasourceRequest.setQuery(TableUtils.tableName2Sql(datasourceSchemaDTO, tableName) + " LIMIT 0 OFFSET 0");
            datasourceRequest.setTable(tableName);
            Provider provider = ProviderFactory.getProvider(datasourceSchemaDTO.getType());
            List<TableField> tableFields = (List<TableField>) provider.fetchTableField(datasourceRequest);
            return tableFields.stream().filter(tableField -> {
                return !tableField.getOriginName().equalsIgnoreCase("crest_row_uuid");
            }).collect(Collectors.toList());
        }

        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
        datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
        datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
        datasourceRequest.setQuery(TableUtils.tableName2Sql(datasourceSchemaDTO, tableName) + " LIMIT 0 OFFSET 0");
        datasourceRequest.setTable(tableName);
        Provider provider = ProviderFactory.getProvider(datasourceSchemaDTO.getType());
        return (List<TableField>) provider.fetchTableField(datasourceRequest);
    }

    /**
     * 手动同步 API 数据源中的单个表，使用该数据源任务配置中的更新范围
     */
    @Override
    public void syncApiTable(Map<String, String> req) throws CrestException {
        String tableName = req.get("tableName");
        String name = req.get("name");
        Long datasourceId = Long.valueOf(req.get("datasourceId"));
        datasourceSyncManage.extractDataForTable(datasourceId, name, tableName, datasourceTaskServer.selectByDSId(datasourceId).getUpdateType());
    }

    /**
     * 手动同步整个 API 数据源，按任务配置的全量或追加策略执行
     */
    @Override
    public void syncApiDs(Map<String, String> req) throws Exception {
        Long datasourceId = Long.valueOf(req.get("datasourceId"));
        CoreDatasourceTask coreDatasourceTask = datasourceTaskServer.selectByDSId(datasourceId);
        CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(datasourceId);
        DatasourceServer.UpdateType updateType = DatasourceServer.UpdateType.valueOf(coreDatasourceTask.getUpdateType());
        datasourceSyncManage.extractedData(null, coreDatasource, updateType, MANUAL.toString());
    }

    private static final Integer replace = 0;
    private static final Integer append = 1;

    /**
     * 上传并解析本地 Excel 文件。追加模式只保留能按工作表名称和字段结构匹配的旧表
     */
    @Override
    public ExcelFileData uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("id") long datasourceId, @RequestParam("editType") Integer editType) throws CrestException {
        validateExcelUploadFile(file);
        CoreDatasource coreDatasource = null;
        if (ObjectUtils.isNotEmpty(datasourceId) && 0L != datasourceId) {
            coreDatasource = dataSourceManage.getCoreDatasource(datasourceId);
        }
        ExcelUtils excelUtils = new ExcelUtils();
        ExcelFileData excelFileData = excelUtils.excelSaveAndParse(file, String.valueOf(AuthUtils.getUser().getUserId()));

        if (Objects.equals(editType, append)) {
            // 追加模式按工作表名称匹配旧表，并要求字段结构一致后才允许复用展示表名
            if (coreDatasource != null) {
                DatasourceRequest datasourceRequest = new DatasourceRequest();
                datasourceRequest.setDatasource(transDTO(coreDatasource));
                List<DatasetTableDTO> datasetTableDTOS = ExcelUtils.tables(datasourceRequest);
                List<ExcelSheetData> excelSheetDataList = new ArrayList<>();
                for (ExcelSheetData sheet : excelFileData.getSheets()) {
                    if (!sheet.isSheet()) {
                        excelSheetDataList.add(sheet);
                        continue;
                    }
                    for (DatasetTableDTO datasetTableDTO : datasetTableDTOS) {
                        if (excelDataTableName(datasetTableDTO.getTableName()).equals(sheet.getTableName())) {
                            List<TableField> newTableFields = sheet.getFields();
                            datasourceRequest.setTable(datasetTableDTO.getTableName());
                            List<TableField> oldTableFields = ExcelUtils.getTableFields(datasourceRequest);
                            if (isEqual(newTableFields, oldTableFields)) {
                                sheet.setDisplayTableName(datasetTableDTO.getTableName());
                                excelSheetDataList.add(sheet);
                            }
                        }
                    }
                }
                excelFileData.setSheets(excelSheetDataList);
            }
        } else {
            // 替换模式允许新工作表继承同名旧表的展示表名，保存时会重建对应引擎表
            if (coreDatasource != null) {
                DatasourceRequest datasourceRequest = new DatasourceRequest();
                datasourceRequest.setDatasource(transDTO(coreDatasource));
                List<DatasetTableDTO> datasetTableDTOS = ExcelUtils.tables(datasourceRequest);
                for (ExcelSheetData sheet : excelFileData.getSheets()) {
                    if (!sheet.isSheet()) {
                        continue;
                    }
                    for (DatasetTableDTO datasetTableDTO : datasetTableDTOS) {
                        if (excelDataTableName(datasetTableDTO.getTableName()).equals(sheet.getTableName())) {
                            sheet.setDisplayTableName(datasetTableDTO.getTableName());
                        }
                    }
                }
            }
        }

        for (ExcelSheetData sheet : excelFileData.getSheets()) {
            if (!sheet.isSheet()) {
                continue;
            }
            for (int i = 0; i < sheet.getFields().size() - 1; i++) {
                for (int j = i + 1; j < sheet.getFields().size(); j++) {
                    if (sheet.getFields().get(i).getName().equalsIgnoreCase(sheet.getFields().get(j).getName())) {
                        CrestException.throwException(sheet.getExcelLabel() + Translator.get("i18n_field_name_repeat") + sheet.getFields().get(i).getName());
                    }
                }
            }
        }
        return excelFileData;
    }

    /**
     * 下载并解析远程 Excel 文件，返回与本地上传一致的工作表和字段预览结构
     */
    @Override
    public ExcelFileData loadRemoteFile(RemoteExcelRequest remoteExcelRequest) throws CrestException, IOException {
        // 远程地址在下载前先做 SSRF 校验，后续解析只使用校验通过的地址
        SsrfProtection.validateUrl(remoteExcelRequest.getUrl());

        remoteExcelRequest.setUserName(decodeBase64RequestValue(remoteExcelRequest.getUserName(), "远程 Excel 用户名"));
        remoteExcelRequest.setPasswd(decodeBase64RequestValue(remoteExcelRequest.getPasswd(), "远程 Excel 密码"));
        ExcelFileData excelFileData = new ExcelUtils().parseRemoteExcel(remoteExcelRequest);
        CoreDatasource coreDatasource = null;
        if (ObjectUtils.isNotEmpty(remoteExcelRequest.getDatasourceId()) && 0L != remoteExcelRequest.getDatasourceId()) {
            coreDatasource = dataSourceManage.getCoreDatasource(remoteExcelRequest.getDatasourceId());
        }
        if (coreDatasource != null) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(transDTO(coreDatasource));
            List<DatasetTableDTO> datasetTableDTOS = ExcelUtils.tables(datasourceRequest);
            for (ExcelSheetData sheet : excelFileData.getSheets()) {
                if (!sheet.isSheet()) {
                    continue;
                }
                for (DatasetTableDTO datasetTableDTO : datasetTableDTOS) {
                    if (excelDataTableName(datasetTableDTO.getTableName()).equals(sheet.getTableName())) {
                        sheet.setDisplayTableName(datasetTableDTO.getTableName());
                    }
                }
            }
        }
        for (ExcelSheetData sheet : excelFileData.getSheets()) {
            if (!sheet.isSheet()) {
                continue;
            }
            for (int i = 0; i < sheet.getFields().size() - 1; i++) {
                for (int j = i + 1; j < sheet.getFields().size(); j++) {
                    if (sheet.getFields().get(i).getName().equalsIgnoreCase(sheet.getFields().get(j).getName())) {
                        CrestException.throwException(sheet.getExcelLabel() + Translator.get("i18n_field_name_repeat") + sheet.getFields().get(i).getName());
                    }
                }
            }
        }
        return excelFileData;
    }

    /**
     * 校验上传文件名和扩展名，只允许 Excel 数据源支持的文件格式进入解析流程
     */
    private void validateExcelUploadFile(MultipartFile file) {
        String fileName = file == null ? null : file.getOriginalFilename();
        String suffix = StringUtils.substringAfterLast(StringUtils.defaultString(fileName), ".").toLowerCase(Locale.ROOT);
        if (!EXCEL_UPLOAD_SUFFIXES.contains(suffix)) {
            CrestException.throwException(Translator.get("i18n_unsupported_file_format"));
        }
        FileUtils.validateUploadFilename(fileName);
    }


    /**
     * 判断追加上传的工作表字段是否能复用旧表结构，并把旧字段的勾选和主键信息带到新字段上
     */
    private boolean isEqual(List<TableField> newTableFields, List<TableField> oldTableFields) {
        if (CollectionUtils.isEmpty(newTableFields) || CollectionUtils.isEmpty(oldTableFields)) {
            return false;
        }
        boolean isHistory = oldTableFields.stream().filter(tableField -> !tableField.isChecked()).collect(Collectors.toList()).size() == oldTableFields.size();
        if (isHistory) {
            oldTableFields.forEach(tableField -> tableField.setChecked(true));
        }
        newTableFields.forEach(tableField -> tableField.setChecked(false));
        for (TableField oldField : oldTableFields) {
            if (!oldField.isChecked()) {
                continue;
            }
            boolean find = false;
            for (TableField newField : newTableFields) {
                if (oldField.getName().equals(newField.getName())) {
                    find = true;
                    newField.setChecked(oldField.isChecked());
                    newField.setPrimaryKey(oldField.isPrimaryKey());
                    newField.setLength(oldField.getLength());
                    break;
                }
            }
            if (!find) {
                return find;
            }
        }
        return true;
    }

    /**
     * 合并旧字段配置到新字段列表，保留用户在历史配置中的勾选、主键和长度设置
     */
    private void mergeFields(List<TableField> oldFields, List<TableField> newFields) {
        newFields.forEach(tableField -> tableField.setChecked(false));
        boolean isHistory = oldFields.stream().filter(tableField -> !tableField.isChecked()).collect(Collectors.toList()).size() == oldFields.size();
        if (isHistory) {
            oldFields.forEach(tableField -> tableField.setChecked(true));
        }
        for (TableField newField : newFields) {
            for (TableField oldField : oldFields) {
                if (oldField.getName().equals(newField.getName())) {
                    newField.setChecked(oldField.isChecked());
                    newField.setPrimaryKey(oldField.isPrimaryKey());
                    newField.setLength(oldField.getLength());
                }
            }
        }
    }

    /**
     * 判断文件名是否为 CSV，用于选择文本解析路径
     */
    private boolean isCsv(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        return suffix.equalsIgnoreCase("csv");
    }

    /**
     * 预检单个 API 定义，解析字段结构并对 Basic Auth 凭证重新编码后返回给前端
     */
    @Override
    public ApiDefinition checkApiDatasource(Map<String, String> request) throws CrestException {
        ApiDefinition apiDefinition = JsonUtil.parseObject(decodeBase64RequestValue(request.get("data"), "API 数据源配置"), ApiDefinition.class);
        apiDefinition.setType("table");
        if (request.keySet().contains("type") && request.get("type").equals("apiStructure")) {
            apiDefinition.setShowApiStructure(true);
        }
        List<ApiDefinition> paramsList = JsonUtil.parseList(decodeBase64RequestValue(request.get("paramsList"), "API 参数配置"), listTypeReference);
        paramsList.add(apiDefinition);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        DatasourceDTO datasource = new DatasourceDTO();
        datasource.setConfiguration(JsonUtil.toJSONString(paramsList).toString());
        datasourceRequest.setDatasource(datasource);

        apiDefinition = (ApiDefinition) invokeMethod(request.get("dsType"), "checkApiDefinition", DatasourceRequest.class, datasourceRequest);
        if (apiDefinition.getRequest().getAuthManager() != null && StringUtils.isNotBlank(apiDefinition.getRequest().getAuthManager().getUsername()) && StringUtils.isNotBlank(apiDefinition.getRequest().getAuthManager().getPassword()) && apiDefinition.getRequest().getAuthManager().getVerification().equals("Basic Auth")) {
            apiDefinition.getRequest().getAuthManager().setUsername(new String(Base64.getEncoder().encode(apiDefinition.getRequest().getAuthManager().getUsername().getBytes())));
            apiDefinition.getRequest().getAuthManager().setPassword(new String(Base64.getEncoder().encode(apiDefinition.getRequest().getAuthManager().getPassword().getBytes())));
        }
        return apiDefinition;
    }

    /**
     * 解析访问令牌接口返回值，并把非成功返回转换为业务异常
     */
    private Map<String, Object> buildAccessTokenResult(String json) {
        if (ObjectUtils.isEmpty(json)) {
            CrestException.throwException("get access token error");
        }
        Map<String, Object> resultMap = JsonUtil.parse(json, Map.class);
        if (Integer.parseInt(resultMap.get("code").toString()) != 0) {
            CrestException.throwException(resultMap.get("msg").toString());
        }
        return resultMap;
    }

    /**
     * 构造访问令牌请求体，调用方负责把结果发送到对应鉴权接口
     */
    private String buildAccessTokenParam(String appId, String appSecret) {
        Map<String, String> param = new HashMap<>();
        param.put("app_id", appId);
        param.put("app_secret", appSecret);
        return Objects.requireNonNull(JsonUtil.toJSONString(param)).toString();
    }

    /**
     * 校验数据源类型是否受支持，内置类型和已加载插件类型都视为合法
     */
    private void preCheckDs(DatasourceDTO datasource) throws CrestException {
        List<String> list = datasourceTypes().stream().map(DatasourceConfiguration.DatasourceType::getType).collect(Collectors.toList());
        if (pluginManage != null) {
            List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
            pluginDatasourceList.stream()
                    .filter(ele -> isDatasourceTypeAllowed(ele.getType()))
                    .forEach(ele -> list.add(ele.getType()));
        }

        if (!list.contains(datasource.getType())) {
            CrestException.throwException("Datasource type not supported.");
        }
    }

    /**
     * 探测数据源连通性并写回状态。文件夹和本地 Excel 不需要远程连接检查
     */
    public void checkDatasourceStatus(DatasourceDTO coreDatasource) {
        if (coreDatasource.getType().equals(DatasourceConfiguration.DatasourceType.Excel.name()) || coreDatasource.getType().equals(DatasourceConfiguration.DatasourceType.folder.name())) {
            return;
        }
        try {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(coreDatasource);
            String status = null;
            if (coreDatasource.getType().startsWith("API")) {
                status = (String) invokeMethod(coreDatasource.getType(), "checkAPIStatus", DatasourceRequest.class, datasourceRequest);
            } else if (coreDatasource.getType().startsWith("Excel")) {
                status = ExcelUtils.checkStatus(datasourceRequest);
            } else {
                Provider provider = ProviderFactory.getProvider(coreDatasource.getType());
                status = provider.checkStatus(datasourceRequest);
            }
            coreDatasource.setStatus(status);
        } catch (CrestException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.debug(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源配置不完整或无法连接");
        }
    }


    /**
     * 预览指定数据表的有限行数据。Excel 表会按用户字段名做别名映射，隐藏系统行标识
     */
    @Override
    public Map<String, Object> previewDataWithLimit(Map<String, Object> req) throws CrestException {
        String tableName = req.get("table").toString();
        Long id = Long.valueOf(req.get("id").toString());
        if (ObjectUtils.isEmpty(tableName) || ObjectUtils.isEmpty(id)) {
            return null;
        }
        CoreDatasource coreDatasource = requireDatasourceAccess(id);
        DatasetTableDTO datasetTableDTO = new DatasetTableDTO();
        datasetTableDTO.setDatasourceId(id);
        if (!tables(datasetTableDTO).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList()).contains(tableName)) {
            CrestException.throwException(Translator.get("i18n_invalid_table_name"));
        }
        String sql = "SELECT * FROM " + quoteIdentifier(tableName);
        if (coreDatasource.getType().contains("Excel")) {
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(transDTO(coreDatasource));
            datasourceRequest.setTable(tableName);
            List<TableField> tableFields = checkedExcelFields(ExcelUtils.getTableFields(datasourceRequest));
            String columns = tableFields.stream()
                    .map(field -> quoteExcelIdentifier(excelDbFieldName(field)) + " AS " + quoteExcelIdentifier(field.getName()))
                    .collect(Collectors.joining(", "));
            if (StringUtils.isNotEmpty(columns)) {
                sql = "SELECT " + columns + " FROM " + quoteIdentifier(tableName);
            }
        }
        sql = new String(Base64.getEncoder().encode(sql.getBytes()));
        PreviewSqlDTO previewSqlDTO = new PreviewSqlDTO();
        previewSqlDTO.setSql(sql);
        previewSqlDTO.setDatasourceId(id);
        previewSqlDTO.setIsCross(false);
        return datasetDataManage.previewSql(previewSqlDTO);
    }

    /**
     * 分页读取本地 Excel 引擎表数据，并补充在线编辑需要的稳定行标识
     */
    @Override
    public ExcelDataPageVO excelDataPage(ExcelDataPageRequest request) throws CrestException {
        if (request == null) {
            CrestException.throwException("无效的 Excel 数据表");
        }
        ExcelEditContext context = buildExcelEditContext(request.getDatasourceId(), request.getTableName(), true);
        int page = Math.max(Optional.ofNullable(request.getPage()).orElse(1), 1);
        int pageSize = Math.max(Optional.ofNullable(request.getPageSize()).orElse(100), 1);
        pageSize = Math.min(pageSize, MAX_EXCEL_EDIT_PAGE_SIZE);
        int offset = (page - 1) * pageSize;

        String columns = context.fields.stream()
                .map(this::excelDbFieldName)
                .map(this::quoteExcelIdentifier)
                .collect(Collectors.joining(", "));
        String query = "SELECT " + quoteIdentifier(EXCEL_ROW_ID_FIELD)
                + (StringUtils.isEmpty(columns) ? "" : ", " + columns)
                + " FROM " + quoteIdentifier(context.tableName)
                + " ORDER BY " + quoteIdentifier(EXCEL_ROW_ID_FIELD)
                + " LIMIT " + pageSize + " OFFSET " + offset;

        EngineRequest engineRequest = buildEngineRequest(context.engine, query);
        Map<String, Object> result;
        try {
            result = calciteProvider.fetchResultField(engineRequest);
        } catch (Exception e) {
            CrestException.throwException(e);
            return null;
        }

        List<String[]> dataList = (List<String[]>) result.get("data");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataList)) {
            for (String[] row : dataList) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put(EXCEL_EDIT_ROW_ID, row.length > 0 ? row[0] : null);
                for (int i = 0; i < context.fields.size(); i++) {
                    item.put(context.fields.get(i).getName(), i + 1 < row.length ? row[i + 1] : null);
                }
                rows.add(item);
            }
        }

        ExcelDataPageVO vo = new ExcelDataPageVO();
        vo.setFields(context.fields);
        vo.setRows(rows);
        vo.setTotal(fetchExcelEditTotal(context.engine, context.tableName));
        vo.setPage(page);
        vo.setPageSize(pageSize);
        return vo;
    }

    /**
     * 保存 Excel 在线编辑的增删改批次，所有变更在同一个引擎事务中执行
     */
    @Override
    public void saveExcelData(ExcelDataSaveRequest request) throws CrestException {
        if (request == null) {
            CrestException.throwException("无效的 Excel 数据表");
        }
        ExcelEditContext context = buildExcelEditContext(request.getDatasourceId(), request.getTableName(), true);
        List<Map<String, Object>> updates = Optional.ofNullable(request.getUpdates()).orElseGet(ArrayList::new);
        List<Map<String, Object>> inserts = Optional.ofNullable(request.getInserts()).orElseGet(ArrayList::new);
        List<String> deletes = Optional.ofNullable(request.getDeletes()).orElseGet(ArrayList::new);
        int changeCount = updates.size() + inserts.size() + deletes.size();
        if (changeCount == 0) {
            return;
        }
        if (changeCount > MAX_EXCEL_EDIT_BATCH_SIZE) {
            CrestException.throwException("单次保存的数据行数不能超过 " + MAX_EXCEL_EDIT_BATCH_SIZE + " 行");
        }

        EngineRequest engineRequest = buildEngineRequest(context.engine, "");
        try {
            calciteProvider.execWithEngineTransaction(engineRequest, (connection, queryTimeout) -> {
                executeExcelDeletes(connection, queryTimeout, context.tableName, deletes);
                executeExcelUpdates(connection, queryTimeout, context.tableName, context.fields, updates);
                executeExcelInserts(connection, queryTimeout, context.tableName, context.fields, inserts);
            });
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 构建 Excel 在线编辑上下文，校验数据源类型、表名合法性，并按需补齐系统行标识列
     */
    private ExcelEditContext buildExcelEditContext(Long datasourceId, String tableName, boolean ensureRowId) {
        if (ObjectUtils.isEmpty(datasourceId) || StringUtils.isBlank(tableName)) {
            CrestException.throwException("无效的 Excel 数据表");
        }
        CoreDatasource coreDatasource = requireDatasourceAccess(datasourceId);
        if (coreDatasource == null || !Strings.CI.equals(coreDatasource.getType(), DatasourceConfiguration.DatasourceType.Excel.name())) {
            CrestException.throwException("仅支持本地 Excel 数据源在线编辑");
        }
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<String> tableNames = ExcelUtils.tables(datasourceRequest).stream()
                .map(DatasetTableDTO::getTableName)
                .collect(Collectors.toList());
        if (!tableNames.contains(tableName)) {
            CrestException.throwException(Translator.get("i18n_invalid_table_name"));
        }
        datasourceRequest.setTable(tableName);
        List<TableField> tableFields = ExcelUtils.getTableFields(datasourceRequest);
        if (tableFields.stream().anyMatch(field -> Strings.CI.equals(field.getName(), EXCEL_ROW_ID_FIELD)
                || Strings.CI.equals(field.getDbFieldName(), EXCEL_ROW_ID_FIELD))) {
            CrestException.throwException("字段名 " + EXCEL_ROW_ID_FIELD + " 为系统保留字段，暂不支持在线编辑");
        }
        CoreEngine engine = engineManage.info();
        if (ensureRowId) {
            ensureExcelRowId(engine, tableName);
        }
        return new ExcelEditContext(engine, tableName, checkedExcelFields(tableFields));
    }

    /**
     * 返回可编辑的 Excel 字段。历史配置没有勾选状态时，默认使用除系统字段外的全部字段
     */
    private List<TableField> checkedExcelFields(List<TableField> tableFields) {
        if (CollectionUtils.isEmpty(tableFields)) {
            return new ArrayList<>();
        }
        List<TableField> checkedFields = tableFields.stream()
                .filter(TableField::isChecked)
                .filter(field -> !Strings.CI.equals(field.getName(), EXCEL_ROW_ID_FIELD))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(checkedFields)) {
            return checkedFields;
        }
        return tableFields.stream()
                .filter(field -> !Strings.CI.equals(field.getName(), EXCEL_ROW_ID_FIELD))
                .collect(Collectors.toList());
    }

    /**
     * 确保 Excel 引擎表存在稳定行标识列，并为历史数据补齐行标识
     */
    private void ensureExcelRowId(CoreEngine engine, String tableName) {
        if (!hasEngineColumn(engine, tableName, EXCEL_ROW_ID_FIELD)) {
            executeEngineSql(engine, "ALTER TABLE " + quoteIdentifier(tableName)
                    + " ADD COLUMN " + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " VARCHAR(64)");
        }
        String uuidExpression = Strings.CI.equalsAny(engine.getType(), "mysql", "obMysql") ? "UUID()" : "RANDOM_UUID()";
        executeEngineSql(engine, "UPDATE " + quoteIdentifier(tableName)
                + " SET " + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " = " + uuidExpression
                + " WHERE " + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " IS NULL OR "
                + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " = ''");
    }

    /**
     * 通过空结果查询判断引擎表是否存在指定列，避免依赖不同数据库的元数据接口
     */
    private boolean hasEngineColumn(CoreEngine engine, String tableName, String columnName) {
        try {
            Map<String, Object> result = calciteProvider.fetchResultField(buildEngineRequest(engine,
                    "SELECT * FROM " + quoteIdentifier(tableName) + " WHERE 1 = 0"));
            List<TableField> fields = (List<TableField>) result.get("fields");
            return fields.stream().anyMatch(field -> Strings.CI.equals(field.getOriginName(), columnName)
                    || Strings.CI.equals(field.getName(), columnName));
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return false;
    }

    /**
     * 读取 Excel 在线编辑表的总行数，分页接口用它返回前端总量
     */
    private Long fetchExcelEditTotal(CoreEngine engine, String tableName) {
        try {
            Map<String, Object> result = calciteProvider.fetchResultField(buildEngineRequest(engine,
                    "SELECT COUNT(1) FROM " + quoteIdentifier(tableName)));
            List<String[]> data = (List<String[]>) result.get("data");
            if (CollectionUtils.isEmpty(data) || data.get(0).length == 0) {
                return 0L;
            }
            return Long.parseLong(data.get(0)[0]);
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return 0L;
    }

    /**
     * 执行内部引擎 SQL，并统一把底层异常转换为业务异常
     */
    private void executeEngineSql(CoreEngine engine, String sql) {
        try {
            calciteProvider.exec(buildEngineRequest(engine, sql));
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 构造只面向内部引擎的请求对象，调用方负责保证 SQL 已经过标识符校验
     */
    private EngineRequest buildEngineRequest(CoreEngine engine, String sql) {
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        engineRequest.setQuery(sql);
        return engineRequest;
    }

    /**
     * 批量删除 Excel 在线编辑行。表名和行标识列只能通过受控标识符拼接，行值使用参数绑定
     */
    @SuppressWarnings("java/sql-injection")
    private void executeExcelDeletes(Connection connection, int queryTimeout, String tableName, List<String> deletes) throws Exception {
        if (CollectionUtils.isEmpty(deletes)) {
            return;
        }
        // 表名和列名已由 quoteIdentifier 校验和转义；JDBC 占位符不能绑定标识符
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String sql = "DELETE FROM " + quoteIdentifier(tableName)
                + " WHERE " + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " = ?";
        @SuppressWarnings("java/sql-injection")
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        try (PreparedStatement statement = preparedStatement) {
            statement.setQueryTimeout(queryTimeout);
            for (String rowId : deletes) {
                statement.setString(1, normalizeRowId(rowId));
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                statement.addBatch();
            }
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
            statement.executeBatch();
        }
    }

    /**
     * 批量更新 Excel 在线编辑行，字段值使用参数绑定，字段名使用保存配置中的受控字段
     */
    @SuppressWarnings("java/sql-injection")
    private void executeExcelUpdates(Connection connection, int queryTimeout, String tableName, List<TableField> fields, List<Map<String, Object>> updates) throws Exception {
        if (CollectionUtils.isEmpty(updates)) {
            return;
        }
        if (CollectionUtils.isEmpty(fields)) {
            CrestException.throwException("Excel 数据表没有可编辑字段");
        }
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String assignments = fields.stream()
                .map(field -> quoteExcelIdentifier(excelDbFieldName(field)) + " = ?")
                .collect(Collectors.joining(", "));
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String sql = "UPDATE " + quoteIdentifier(tableName) + " SET " + assignments
                + " WHERE " + quoteIdentifier(EXCEL_ROW_ID_FIELD) + " = ?";
        @SuppressWarnings("java/sql-injection")
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        try (PreparedStatement statement = preparedStatement) {
            statement.setQueryTimeout(queryTimeout);
            for (Map<String, Object> row : updates) {
                int index = bindExcelFieldValues(statement, fields, row, 1);
                statement.setString(index, normalizeRowId(row.get(EXCEL_EDIT_ROW_ID)));
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                statement.addBatch();
            }
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
            statement.executeBatch();
        }
    }

    /**
     * 批量插入 Excel 在线编辑行，并为每一行生成新的系统行标识
     */
    @SuppressWarnings("java/sql-injection")
    private void executeExcelInserts(Connection connection, int queryTimeout, String tableName, List<TableField> fields, List<Map<String, Object>> inserts) throws Exception {
        if (CollectionUtils.isEmpty(inserts)) {
            return;
        }
        if (CollectionUtils.isEmpty(fields)) {
            CrestException.throwException("Excel 数据表没有可编辑字段");
        }
        List<String> columnNames = fields.stream().map(this::excelDbFieldName).collect(Collectors.toCollection(ArrayList::new));
        columnNames.add(EXCEL_ROW_ID_FIELD);
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String columns = columnNames.stream().map(this::quoteExcelIdentifier).collect(Collectors.joining(", "));
        String placeholders = columnNames.stream().map(name -> "?").collect(Collectors.joining(", "));
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String sql = "INSERT INTO " + quoteIdentifier(tableName) + " (" + columns + ") VALUES (" + placeholders + ")";
        @SuppressWarnings("java/sql-injection")
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        try (PreparedStatement statement = preparedStatement) {
            statement.setQueryTimeout(queryTimeout);
            for (Map<String, Object> row : inserts) {
                int index = bindExcelFieldValues(statement, fields, row, 1);
                statement.setString(index, UUID.randomUUID().toString());
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                statement.addBatch();
            }
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
            statement.executeBatch();
        }
    }

    /**
     * 按字段顺序绑定 Excel 行值，并返回下一个可用的 JDBC 参数位置
     */
    private int bindExcelFieldValues(PreparedStatement statement, List<TableField> fields, Map<String, Object> row, int startIndex) throws Exception {
        int index = startIndex;
        for (TableField field : fields) {
            Object normalized = normalizeExcelCellValue(field, excelCellValue(row, field));
            statement.setObject(index++, normalized);
        }
        return index;
    }

    /**
     * 按展示名优先读取编辑行中的单元格值，兼容历史字段原始名
     */
    private Object excelCellValue(Map<String, Object> row, TableField field) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(field.getName())) {
            return row.get(field.getName());
        }
        return row.get(field.getOriginName());
    }

    /**
     * 按字段类型把编辑值转换为引擎可写入的值，数值和布尔字段会做格式校验
     */
    private Object normalizeExcelCellValue(TableField field, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        Integer type = field.getExtractedFieldType() == null ? field.getFieldType() : field.getExtractedFieldType();
        try {
            if (Objects.equals(type, 2)) {
                return new BigDecimal(text).toBigIntegerExact().longValueExact();
            }
            if (Objects.equals(type, 3)) {
                return new BigDecimal(text);
            }
            if (Objects.equals(type, 4)) {
                if (Strings.CI.equalsAny(text, "true", "1", "是", "yes")) {
                    return 1;
                }
                if (Strings.CI.equalsAny(text, "false", "0", "否", "no")) {
                    return 0;
                }
                CrestException.throwException("字段 " + field.getName() + " 需要布尔值");
            }
        } catch (ArithmeticException | NumberFormatException e) {
            CrestException.throwException("字段 " + field.getName() + " 的数值格式不正确");
        }
        return text;
    }

    /**
     * 校验并转换在线编辑行标识，缺失行标识时要求前端刷新后重试
     */
    private String normalizeRowId(Object rowId) {
        if (rowId == null || StringUtils.isBlank(String.valueOf(rowId))) {
            CrestException.throwException("缺少数据行标识，请刷新后重试");
        }
        return String.valueOf(rowId);
    }

    /**
     * 校验并转义引擎表名或系统列名，所有内部 SQL 拼接都必须通过该方法处理标识符
     */
    private String quoteIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            CrestException.throwException("Illegal table name");
        }
        EngineProvider.validateSqlInjectionRisk(identifier);
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 转义 Excel 字段名为引擎 SQL 标识符，字段名来源于保存后的受控配置
     */
    private String quoteExcelIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            CrestException.throwException("无效的 Excel 字段名");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 获取 Excel 字段对应的引擎列名，旧配置缺少内部列名时回退到展示名
     */
    private String excelDbFieldName(TableField field) {
        return StringUtils.defaultIfBlank(field.getDbFieldName(), field.getName());
    }

    /**
     * Excel 在线编辑上下文，集中保存引擎配置、目标表名和可编辑字段
     */
    private static class ExcelEditContext {
        private final CoreEngine engine;
        private final String tableName;
        private final List<TableField> fields;

        /**
         * 创建一次在线编辑请求使用的不可变上下文
         */
        private ExcelEditContext(CoreEngine engine, String tableName, List<TableField> fields) {
            this.engine = engine;
            this.tableName = tableName;
            this.fields = fields;
        }
    }

    /**
     * 返回当前用户最近使用过的非目录数据源类型，最多保留五种
     */
    @Override
    public List<String> latestUse() {
        List<String> types = new ArrayList<>();
        QueryWrapper<CoreDatasource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("create_by", AuthUtils.getUser().getUserId());
        queryWrapper.orderByDesc("create_time");
        queryWrapper.last(dialect().limitOffset("", 5, 0));
        List<CoreDatasource> coreDatasources = datasourceMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(coreDatasources)) {
            return types;
        }
        for (CoreDatasource coreDatasource : coreDatasources) {
            if (!coreDatasource.getType().equalsIgnoreCase("folder") && !types.contains(coreDatasource.getType())) {
                types.add(coreDatasource.getType());
            }
        }
        return types;
    }

    /**
     * 分页读取数据源同步日志，并把任务日志中的内部表名补充为展示名称
     */
    @Override
    public IPage<CoreDatasourceTaskLogDTO> listSyncRecord(int goPage, int pageSize, Long dsId) {
        QueryWrapper<CoreDatasourceTaskLogDTO> wrapper = new QueryWrapper<>();
        wrapper.eq("ds_id", dsId);
        wrapper.orderByDesc("start_time");
        Page<CoreDatasourceTaskLogDTO> page = new Page<>(goPage, pageSize);
        IPage<CoreDatasourceTaskLogDTO> pager = taskLogExtMapper.pager(page, wrapper);
        CoreDatasource coreDatasource = dataSourceManage.getCoreDatasource(dsId);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<DatasetTableDTO> datasetTableDTOS = new ArrayList<>();
        if (coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.toString())) {
            datasetTableDTOS = (List<DatasetTableDTO>) invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
        } else {
            datasetTableDTOS = ExcelUtils.tables(datasourceRequest);
        }
        for (int i = 0; i < pager.getRecords().size(); i++) {
            for (int i1 = 0; i1 < datasetTableDTOS.size(); i1++) {
                if (pager.getRecords().get(i).getTableName().equalsIgnoreCase(datasetTableDTOS.get(i1).getTableName())) {
                    pager.getRecords().get(i).setName(datasetTableDTOS.get(i1).getName());
                }
            }
        }
        return pager;
    }


    /**
     * 异步刷新所有非文件夹、非本地 Excel 数据源状态，避免同一数据源重复入队
     */
    public void updateDatasourceStatus() {
        QueryWrapper<CoreDatasource> wrapper = new QueryWrapper<>();
        wrapper.notIn("type", Arrays.asList("Excel", "folder"));
        List<CoreDatasource> datasources = datasourceMapper.selectList(wrapper);
        datasources.forEach(datasource -> {
            if (!syncDsIds.contains(datasource.getId())) {
                syncDsIds.add(datasource.getId());
                commonThreadPool.addTask(() -> {
                    try {
                        validate(datasource);
                    } catch (Exception e) {
                        LogUtil.error(e.getMessage(), e);
                    } finally {
                        syncDsIds.removeIf(id -> id.equals(datasource.getId()));
                    }
                });
            }
        });
    }

    /**
     * 修复调度节点异常停止后的数据源任务状态，单实例内用标记避免并发执行
     */
    public void updateStopJobStatus() {
        if (this.isUpdatingStatus) {
            return;
        } else {
            this.isUpdatingStatus = true;
        }

        try {
            doUpdate();
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        } finally {
            this.isUpdatingStatus = false;
        }
    }

    /**
     * 查找已经不在活跃调度实例上的执行中任务，并回滚为等待执行状态
     */
    private void doUpdate() {
        List<CoreScheduleState> scheduleStates = coreScheduleStateMapper.selectList(null);
        List<String> activeScheduleInstances = scheduleStates.stream().filter(scheduleState -> scheduleState.getLastCheckinTime() + scheduleState.getCheckinInterval() + 1000 > dataSourceExtMapper.selectTimestamp().getCurrentTimestamp() * 1000).map(CoreScheduleState::getInstanceName).collect(Collectors.toList());

        QueryWrapper<CoreDatasource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("task_status", TaskStatus.UnderExecution.name());
        List<CoreDatasource> datasources = datasourceMapper.selectList(queryWrapper);

        List<CoreDatasource> syncCoreDatasources = new ArrayList<>();
        List<CoreDatasource> jobStoppedCoreDatasources = new ArrayList<>();
        datasources.forEach(coreDatasource -> {
            if (StringUtils.isNotEmpty(coreDatasource.getSchedulerFireInstanceId()) && !activeScheduleInstances.contains(coreDatasource.getSchedulerFireInstanceId().substring(0, coreDatasource.getSchedulerFireInstanceId().length() - 13))) {
                jobStoppedCoreDatasources.add(coreDatasource);
            } else {
                syncCoreDatasources.add(coreDatasource);
            }
        });

        if (CollectionUtils.isEmpty(jobStoppedCoreDatasources)) {
            return;
        }

        queryWrapper.clear();
        queryWrapper.in("id", jobStoppedCoreDatasources.stream().map(CoreDatasource::getId).collect(Collectors.toList()));
        CoreDatasource record = new CoreDatasource();
        record.setTaskStatus(TaskStatus.WaitingForExecution.name());
        datasourceMapper.update(record, queryWrapper);
        // 同步任务状态要和数据源任务状态一起回滚，避免页面继续显示执行中
        datasourceTaskServer.updateByDsIds(jobStoppedCoreDatasources.stream().map(CoreDatasource::getId).collect(Collectors.toList()));
    }

    /**
     * 判断当前用户是否还需要展示数据源引导完成页
     */
    @Override
    public boolean showFinishPage() throws CrestException {
        return coreDsFinishPageMapper.selectById(AuthUtils.getUser().getUserId()) == null;
    }


    /**
     * 记录当前用户已经看过数据源引导完成页
     */
    @Override
    public void setShowFinishPage() throws CrestException {
        CoreDsFinishPage coreDsFinishPage = new CoreDsFinishPage();
        coreDsFinishPage.setId(AuthUtils.getUser().getUserId());
        coreDsFinishPageMapper.insert(coreDsFinishPage);
    }

    /**
     * 把持久化实体复制为接口传输对象，供 provider 和前端流程共用
     */
    private DatasourceDTO transDTO(CoreDatasource record) {
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, record);
        return datasourceDTO;
    }


    /**
     * 在数据源树中递归收集同类型节点 ID，重复连接检查会排除当前正在编辑的节点
     */
    private void filterDs(List<BusiNodeVO> busiNodeVOS, List<Long> ids, String type, Long id) {
        for (BusiNodeVO busiNodeVO : busiNodeVOS) {
            if (busiNodeVO.getType() != null && busiNodeVO.getType().equalsIgnoreCase(type)) {
                if (id != null) {
                    if (!busiNodeVO.getId().equals(id)) {
                        ids.add(busiNodeVO.getId());
                    }
                } else {
                    ids.add(busiNodeVO.getId());
                }
            }
            if (CollectionUtils.isNotEmpty(busiNodeVO.getChildren())) {
                filterDs(busiNodeVO.getChildren(), ids, type, id);
            }
        }
    }

    /**
     * 校验连接池配置的数值关系，防止保存后创建不可用的连接池
     */
    private static void checkParams(String configurationStr) {
        DatasourceConfiguration configuration = JsonUtil.parseObject(configurationStr, DatasourceConfiguration.class);
        if (configuration == null) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源配置格式无效");
        }
        if (configuration.getInitialPoolSize() < configuration.getMinPoolSize()) {
            CrestException.throwException("初始连接数不能小于最小连接数！");
        }
        if (configuration.getInitialPoolSize() > configuration.getMaxPoolSize()) {
            CrestException.throwException("初始连接数不能大于最大连接数！");
        }
        if (configuration.getMaxPoolSize() < configuration.getMinPoolSize()) {
            CrestException.throwException("最大连接数不能小于最小连接数！");
        }
        if (configuration.getQueryTimeout() < 0) {
            CrestException.throwException("查询超时不能小于0！");
        }
    }

    /**
     * 校验连接型数据源的必填连接配置。自定义 JDBC 地址模式只要求 JDBC 地址存在
     */
    private static void checkDatasourceConfigurationComplete(DatasourceDTO datasource) {
        String type = datasource.getType();
        if (StringUtils.isBlank(type)) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源类型不能为空");
        }
        if (notFullDs.stream().anyMatch(item -> Strings.CI.contains(type, item))) {
            return;
        }
        DatasourceConfiguration configuration = JsonUtil.parseObject(datasource.getConfiguration(), DatasourceConfiguration.class);
        if (configuration == null) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源配置格式无效");
        }
        boolean customJdbcUrl = StringUtils.isNotBlank(configuration.getUrlType())
                && !Strings.CI.equals(configuration.getUrlType(), "hostName");
        if (customJdbcUrl) {
            if (StringUtils.isBlank(configuration.getJdbcUrl())) {
                CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源 JDBC 地址不能为空");
            }
            return;
        }
        if (StringUtils.isBlank(configuration.getHost())) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源地址不能为空");
        }
        if (configuration.getPort() == null) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据源端口不能为空");
        }
        if (requiresDatabaseName(type, configuration)) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "数据库名称不能为空");
        }
    }

    // OB Oracle 连接没有 MySQL 意义上的数据库名，依赖 schema 或用户名定位当前模式。
    private static boolean requiresDatabaseName(String type, DatasourceConfiguration configuration) {
        if (Strings.CI.equals(type, DatasourceConfiguration.DatasourceType.obOracle.getType())) {
            return StringUtils.isBlank(configuration.getSchema()) && StringUtils.isBlank(configuration.getUsername());
        }
        return StringUtils.isBlank(configuration.getDataBase());
    }

    /**
     * 校验同一 API 或 Excel 数据源内的展示表名不能重复
     */
    private static void checkName(List<String> tables) {
        for (int i = 0; i < tables.size() - 1; i++) {
            for (int j = i + 1; j < tables.size(); j++) {
                if (tables.get(i).equalsIgnoreCase(tables.get(j))) {
                    CrestException.throwException(Translator.get("i18n_table_name_repeat") + tables.get(i));
                }
            }
        }
    }

    /**
     * 从系统生成的 Excel 引擎表名中还原工作表名称，用于上传时匹配旧表
     */
    private String excelDataTableName(String name) {
        return StringUtils.substring(name, 6, name.length() - 11);
    }

    /**
     * 按 ID 读取数据源并转换为接口对象，可按调用场景决定是否隐藏凭证
     */
    private DatasourceDTO datasourceDTOById(Long datasourceId, boolean hidePw) throws CrestException {
        CoreDatasource datasource = requireDatasourceAccess(datasourceId);
        if (datasource == null) {
            CrestException.throwException(Translator.get("i18n_datasource_not_exists"));
        }
        return convertCoreDatasource(datasourceId, hidePw, datasource);
    }

    /**
     * 将持久化数据源转换为前端详情对象，补齐 API 状态、同步设置、文件信息和创建人名称
     */
    private DatasourceDTO convertCoreDatasource(Long datasourceId, boolean hidePw, CoreDatasource datasource) {
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, datasource);

        if (datasourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.toString())) {
            List<ApiDefinition> apiDefinitionList = JsonUtil.parseList(datasourceDTO.getConfiguration(), listTypeReference);
            List<ApiDefinition> apiDefinitionListWithStatus = new ArrayList<>();
            List<ApiDefinition> params = new ArrayList<>();
            int success = 0;
            for (ApiDefinition apiDefinition : apiDefinitionList) {
                String status = null;
                if (StringUtils.isNotEmpty(datasourceDTO.getStatus())) {
                    JsonNode jsonNode = null;
                    try {
                        jsonNode = objectMapper.readTree(datasourceDTO.getStatus());
                        for (JsonNode node : jsonNode) {
                            if (node.get("name").asText().equals(apiDefinition.getName())) {
                                status = node.get("status").asText();
                            }
                        }
                        apiDefinition.setStatus(status);
                    } catch (Exception ignore) {
                    }
                }
                if (StringUtils.isNotEmpty(status) && status.equalsIgnoreCase("Success")) {
                    success++;
                }
                CoreDatasourceTaskLog log = datasourceTaskServer.lastSyncLogForTable(datasourceId, apiDefinition.getDisplayTableName());
                if (log != null) {
                    apiDefinition.setUpdateTime(log.getStartTime());
                }


                if (StringUtils.isEmpty(apiDefinition.getType()) || apiDefinition.getType().equalsIgnoreCase("table")) {
                    apiDefinitionListWithStatus.add(apiDefinition);
                } else {
                    params.add(apiDefinition);
                }
            }
            if (CollectionUtils.isNotEmpty(params)) {
                datasourceDTO.setParamsStr(RsaUtils.symmetricEncrypt(JsonUtil.toJSONString(params).toString()));
            }
            if (CollectionUtils.isNotEmpty(apiDefinitionListWithStatus)) {
                datasourceDTO.setApiConfigurationStr(RsaUtils.symmetricEncrypt(JsonUtil.toJSONString(apiDefinitionListWithStatus).toString()));
            }
            if (success == apiDefinitionList.size()) {
                datasourceDTO.setStatus("Success");
            } else {
                if (success > 0 && success < apiDefinitionList.size()) {
                    datasourceDTO.setStatus("Warning");
                } else {
                    datasourceDTO.setStatus("Error");
                }
            }
        } else {
            if (hidePw) {
                Provider provider = ProviderFactory.getProvider(datasourceDTO.getType());
                provider.hidePW(datasourceDTO);
            }
        }
        if (datasourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.Excel.toString())) {
            datasourceDTO.setFileName(ExcelUtils.getFileName(datasource));
            datasourceDTO.setSize(ExcelUtils.getSize(datasource));
        }
        if (datasourceDTO.getType().equalsIgnoreCase(DatasourceConfiguration.DatasourceType.ExcelRemote.name()) || datasourceDTO.getType().contains(DatasourceConfiguration.DatasourceType.API.toString())) {
            CoreDatasourceTask coreDatasourceTask = datasourceTaskServer.selectByDSId(datasourceDTO.getId());
            TaskDTO taskDTO = new TaskDTO();
            BeanUtils.copyBean(taskDTO, coreDatasourceTask);
            datasourceDTO.setSyncSetting(taskDTO);
            CoreDatasourceTask task = datasourceTaskServer.selectByDSId(datasourceDTO.getId());
            if (task != null) {
                datasourceDTO.setLastSyncTime(task.getStartTime());
            }
        }
        datasourceDTO.setConfiguration(RsaUtils.symmetricEncrypt(datasourceDTO.getConfiguration()));
        datasourceDTO.setCreator(coreUserManage.getUserName(Long.valueOf(datasourceDTO.getCreateBy())));
        return datasourceDTO;
    }

    /**
     * 校验持久化数据源状态并写回数据库，连接型数据源校验成功后会刷新连接池
     */
    private DatasourceDTO validate(CoreDatasource coreDatasource) {
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, coreDatasource);
        try {
            checkDatasourceStatus(datasourceDTO);
            if (!Arrays.asList("API", "Excel", "folder").contains(coreDatasource.getType()) && !coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.API.name()) && !coreDatasource.getType().contains(DatasourceConfiguration.DatasourceType.ExcelRemote.name())) {
                calciteProvider.updateDsPoolAfterCheckStatus(datasourceDTO);
            }
        } catch (CrestException e) {
            datasourceDTO.setStatus("Error");
            throw e;
        } catch (Exception e) {
            datasourceDTO.setStatus("Error");
            CrestException.throwException(e);
        } finally {
            coreDatasource.setStatus(datasourceDTO.getStatus());
            dataSourceManage.innerEditStatus(coreDatasource);
        }
        datasourceDTO.setConfiguration("");
        return datasourceDTO;
    }

    /**
     * 返回数据源简要信息，尽量只暴露名称、类型、描述和主机地址
     */
    @Override
    public DsSimpleVO simple(Long id) {
        if (ObjectUtils.isEmpty(id)) CrestException.throwException("id is null");
        CoreDatasource coreDatasource = requireDatasourceAccess(id);
        if (ObjectUtils.isEmpty(coreDatasource)) return null;
        DsSimpleVO vo = new DsSimpleVO();
        vo.setName(coreDatasource.getName());
        vo.setType(coreDatasource.getType());
        vo.setDescription(coreDatasource.getDescription());
        String configuration = coreDatasource.getConfiguration();
        DatasourceConfiguration config = null;
        String host = null;
        if (StringUtils.isBlank(configuration)
                || Strings.CI.equals("[]", configuration)
                || ObjectUtils.isEmpty(config = JsonUtil.parseObject(configuration, DatasourceConfiguration.class))
                || StringUtils.isBlank(host = config.getHost())) {
            return vo;
        }
        vo.setHost(host);
        return vo;
    }

    /**
     * 读取数据源并校验当前用户创建者权限，所有修改和敏感读取入口都应先调用该方法
     */
    private CoreDatasource requireDatasourceAccess(Long datasourceId) {
        CoreDatasource datasource = dataSourceManage.getCoreDatasource(datasourceId);
        if (datasource == null) {
            CrestException.throwException(Translator.get("i18n_datasource_not_exists"));
        }
        CrestPermissionUtils.requireCreator(datasource.getCreateBy());
        return datasource;
    }

    // 数据源模块的元数据查询尾部语法按当前系统库方言生成，避免 OB Oracle 误用 MySQL LIMIT
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    /**
     * 预览 API 数据源的多维表或视图列表，具体实现由对应 API provider 提供
     */
    @Override
    public List<Map<String, String>> multidimensionalTables(Map<String, String> request) throws CrestException {
        List<ApiDefinition> paramsList = new ArrayList<>();
        ApiDefinition apiDefinition = JsonUtil.parseObject(decodeBase64RequestValue(request.get("data"), "API 数据源配置"), ApiDefinition.class);
        paramsList.add(apiDefinition);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        DatasourceDTO datasource = new DatasourceDTO();
        datasource.setConfiguration(JsonUtil.toJSONString(paramsList).toString());
        datasourceRequest.setDatasource(datasource);
        List<Map<String, String>> result = new ArrayList<>();
        if (request.keySet().contains("type") && request.get("type").equals("tables")) {
            result = (List<Map<String, String>>) invokeMethod(request.get("dsType"), "listTables", DatasourceRequest.class, datasourceRequest);
        }
        if (request.keySet().contains("type") && request.get("type").equals("views")) {
            result = (List<Map<String, String>>) invokeMethod(request.get("dsType"), "listViews", DatasourceRequest.class, datasourceRequest);
        }
        return result;
    }

    /**
     * 查找 API provider 的静态方法或插件 provider 方法，作为 API 数据源适配层的统一入口
     */
    private Method getMethod(String dsType, String methodName, Class<?> classes) {
        Method method = null;
        try {
            String ClassName = "io.crest.datasource.provider.ApiUtils";
            if (!dsType.equals(DatasourceConfiguration.DatasourceType.API.name())) {
                Provider provider = ProviderFactory.getProvider(dsType);
                method = provider.getClass().getMethod(methodName, classes);
            } else {
                Class<?> clazz = Class.forName(ClassName);
                method = clazz.getMethod(methodName, classes);
            }

        } catch (Exception e) {
            CrestException.throwException("Cant find method: " + e.getMessage());
        }
        return method;
    }

    /**
     * 调用数据源 provider 方法，并把反射异常还原为业务异常消息
     */
    public Object invokeMethod(String dsType, String methodName, Class<?> classes, Object object) {
        Object resObj = null;
        try {
            Method method = getMethod(dsType, methodName, classes);
            resObj = method.invoke(null, object);
        } catch (Exception e) {
            CrestException.throwException(msg(e));
        }
        return resObj;
    }

    /**
     * 展开反射调用异常链，优先返回最接近业务异常的错误消息
     */
    private String msg(Throwable e) {
        Throwable exception = e;
        while (true) {
            if (exception.getCause() == null) {
                return exception.getMessage();
            }
            if (exception instanceof CrestException && (!(exception.getCause() instanceof CrestException) && !(exception.getCause() instanceof InvocationTargetException))) {
                return exception.getMessage();
            }
            exception = exception.getCause();

        }
    }
}
