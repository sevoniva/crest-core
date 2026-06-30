package io.crest.datasource.provider;


import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.api.ds.vo.ExcelFileData;
import io.crest.api.ds.vo.ExcelSheetData;
import io.crest.api.ds.vo.RemoteExcelRequest;
import io.crest.commons.utils.EncryptUtils;
import io.crest.dataset.utils.FieldUtils;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.api.ds.vo.ExcelConfiguration;
import io.crest.i18n.Translator;
import io.crest.storage.LocalStorageService;
import io.crest.storage.StorageService;
import io.crest.utils.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Excel 数据源工具，负责本地上传、远程下载、工作表识别、字段推断和数据读取
 * 该类只处理文件形态和字段结构转换，不负责数据源权限、任务调度和持久化事务
 */
@SuppressWarnings({"deprecation", "unchecked"})
public class ExcelUtils {
    public static final String UFEFF = "\uFEFF";
    private static final int EXCEL_FIELD_DISPLAY_NAME_LIMIT = 100;
    private static final String DEFAULT_EXCEL_FIELD_TYPE = "TEXT";
    private static final String SHEET_STATUS_NORMAL = "NORMAL";
    private static final String SHEET_STATUS_SKIPPED = "SKIPPED";
    private static final String SHEET_MESSAGE_SQL_DESCRIPTION = "检测到该工作表首行疑似 SQL 或说明文本，默认不导入。";
    private static final String SHEET_MESSAGE_EMPTY_HEADER = "未检测到有效表头，默认不导入。";
    private static final String SHEET_MESSAGE_BLANK_HEADER = "检测到空表头，默认不导入。";
    private static final StorageService FALLBACK_STORAGE_SERVICE = new LocalStorageService();
    private static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 返回 Excel 临时文件和上传文件的根目录，并保证调用方拿到以分隔符结尾的路径
     */
    public static String getExcelPath() {
        String configuredPath = ConfigUtils.getConfig("crest.path.excel", "/opt/crest/data/excel/");
        if (StringUtils.isBlank(configuredPath)) {
            configuredPath = "/opt/crest/data/excel/";
        }
        return configuredPath.endsWith(File.separator) ? configuredPath : configuredPath + File.separator;
    }

    /**
     * 将文件名限制在 Excel 根目录下，防止调用方通过相对路径访问目录外文件
     */
    private static File excelTempFile(String filename) {
        return storageService().resolve(getExcelPath(), filename);
    }

    /**
     * 兼容历史配置中保存的绝对路径，但仍要求文件位于 Excel 根目录下。
     */
    @SuppressWarnings("java/path-injection")
    private static File excelStoredFile(String storedPath) {
        if (StringUtils.isBlank(storedPath) || storedPath.indexOf('\0') >= 0) {
            CrestException.throwException("Excel文件路径非法");
        }
        try {
            File root = new File(getExcelPath()).getCanonicalFile();
            File target = new File(storedPath);
            if (!target.isAbsolute()) {
                target = new File(root, storedPath);
            }
            File canonicalTarget = target.getCanonicalFile();
            if (!canonicalTarget.toPath().startsWith(root.toPath())) {
                CrestException.throwException("Excel文件路径越界");
            }
            Path relativePath = root.toPath().relativize(canonicalTarget.toPath());
            List<String> pathSegments = new ArrayList<>();
            for (Path pathSegment : relativePath) {
                pathSegments.add(pathSegment.toString());
            }
            if (pathSegments.isEmpty()) {
                CrestException.throwException("Excel文件路径非法");
            }
            return storageService().resolve(root.getPath(), pathSegments.toArray(new String[0]));
        } catch (IOException | IllegalArgumentException e) {
            CrestException.throwException("Excel文件路径解析失败");
            return new File(storedPath);
        }
    }

    /**
     * 通过存储抽象读取 Excel 文件，兼容历史绝对路径配置。
     */
    private static InputStream newExcelInputStream(File file) throws IOException {
        return storageService().newInputStream(file);
    }

    /**
     * 通过存储抽象写入 Excel 文件，生产环境由 RWX PVC 提供共享目录。
     */
    private static OutputStream newExcelOutputStream(File file) throws IOException {
        return storageService().newOutputStream(file);
    }

    /**
     * 删除 Excel 临时文件，仅允许删除 Excel 根目录下的文件名。
     */
    private static void deleteExcelTempFile(String filename) {
        if (StringUtils.isNotEmpty(filename)) {
            storageService().deleteFile(getExcelPath(), filename);
        }
    }

    /**
     * 静态工具类优先使用容器内存储服务，单元测试和旧静态调用退回本地实现。
     */
    private static StorageService storageService() {
        StorageService storageService = CommonBeanFactory.getBean(StorageService.class);
        return storageService == null ? FALLBACK_STORAGE_SERVICE : storageService;
    }

    private static TypeReference<List<TableField>> TableFieldListTypeReference = new TypeReference<List<TableField>>() {
    };

    private static TypeReference<List<ExcelSheetData>> sheets = new TypeReference<List<ExcelSheetData>>() {
    };

    /**
     * 将展示表头转换为内部字段名，保持与数据集字段命名规则一致
     */
    static String excelDbFieldName(String header) {
        return TableUtils.fieldNameShort(StringUtils.defaultString(header).trim());
    }

    /**
     * 根据首行表头生成字段列表，同时处理展示名和内部字段名的重复问题
     */
    private List<TableField> excelTableFields(List<String> headers) {
        List<TableField> fields = new ArrayList<>();
        Map<String, Integer> displayNameCounts = new HashMap<>();
        Set<String> usedDisplayNames = new HashSet<>();
        Set<String> usedDbFieldNames = new HashSet<>();
        for (String header : headers) {
            fields.add(excelTableField(uniqueExcelDisplayName(normalizeExcelHeader(header), displayNameCounts, usedDisplayNames), usedDbFieldNames));
        }
        return fields;
    }

    /**
     * 创建单个 Excel 字段描述，字段类型会在预览数据扫描阶段再推断
     */
    private TableField excelTableField(String displayName, Set<String> usedDbFieldNames) {
        TableField tableField = new TableField();
        tableField.setName(displayName);
        tableField.setOriginName(displayName);
        tableField.setDbFieldName(uniqueExcelDbFieldName(displayName, usedDbFieldNames));
        return tableField;
    }

    /**
     * 为重复表头生成稳定的展示名，避免前端字段选择和 JSON 行数据出现同名键
     */
    private String uniqueExcelDisplayName(String displayName, Map<String, Integer> displayNameCounts, Set<String> usedDisplayNames) {
        int count = displayNameCounts.getOrDefault(displayName, 0) + 1;
        displayNameCounts.put(displayName, count);
        String candidate = count == 1 ? displayName : excelDuplicateDisplayName(displayName, count);
        while (!usedDisplayNames.add(candidate)) {
            count = displayNameCounts.getOrDefault(displayName, count) + 1;
            displayNameCounts.put(displayName, count);
            candidate = excelDuplicateDisplayName(displayName, count);
        }
        return candidate;
    }

    /**
     * 生成重复表头后缀，并在最大长度内保留后缀，确保字段名仍可区分
     */
    private String excelDuplicateDisplayName(String displayName, int count) {
        String suffix = "_" + count;
        if (displayName.length() + suffix.length() <= EXCEL_FIELD_DISPLAY_NAME_LIMIT) {
            return displayName + suffix;
        }
        return displayName.substring(0, EXCEL_FIELD_DISPLAY_NAME_LIMIT - suffix.length()) + suffix;
    }

    /**
     * 为内部字段名追加序号直到唯一，避免建表或查询时出现列名冲突
     */
    private String uniqueExcelDbFieldName(String displayName, Set<String> usedDbFieldNames) {
        String dbFieldName = excelDbFieldName(displayName);
        int count = 2;
        while (!usedDbFieldNames.add(dbFieldName)) {
            dbFieldName = excelDbFieldName(displayName + "_" + count);
            count++;
        }
        return dbFieldName;
    }

    /**
     * 补齐字段类型、原生类型和抽取类型，保证旧配置和新推断字段都能进入统一数据集流程
     */
    private static void normalizeExcelTableField(TableField field) {
        String type = StringUtils.firstNonBlank(field.getType(), field.getNativeType(), DEFAULT_EXCEL_FIELD_TYPE);
        field.setType(type);
        if (StringUtils.isBlank(field.getNativeType())) {
            field.setNativeType(type);
        }
        int fieldType = FieldUtils.resolveFieldType(type);
        if (field.getFieldType() == null) {
            field.setFieldType(fieldType);
        }
        if (field.getExtractedFieldType() == null) {
            field.setExtractedFieldType(fieldType);
        }
    }

    /**
     * 校验并整理表头文本。空表头和超长表头会直接阻断导入，避免生成不可维护字段
     */
    private String normalizeExcelHeader(String header) {
        String displayName = StringUtils.trim(header);
        if (StringUtils.isEmpty(displayName)) {
            CrestException.throwException(Translator.get("i18n_excel_error_first_row"));
        }
        if (displayName.length() > EXCEL_FIELD_DISPLAY_NAME_LIMIT) {
            CrestException.throwException("Excel 表头不能超过 100 个字符：" + StringUtils.abbreviate(displayName, 30));
        }
        return displayName;
    }

    /**
     * 判断工作表是否可导入，并给出跳过原因，供预览界面直接展示
     */
    private ExcelSheetInspection inspectSheet(List<String> header) {
        if (CollectionUtils.isEmpty(header)) {
            return ExcelSheetInspection.skipped(SHEET_MESSAGE_EMPTY_HEADER);
        }
        if (header.stream().allMatch(StringUtils::isBlank)) {
            return ExcelSheetInspection.skipped(SHEET_MESSAGE_EMPTY_HEADER);
        }
        if (header.stream().anyMatch(StringUtils::isBlank)) {
            return ExcelSheetInspection.skipped(SHEET_MESSAGE_BLANK_HEADER);
        }
        if (isSqlDescriptionSheet(header)) {
            return ExcelSheetInspection.skipped(SHEET_MESSAGE_SQL_DESCRIPTION);
        }
        return ExcelSheetInspection.normal();
    }

    /**
     * 识别首行疑似 SQL 或说明文本的工作表，避免把说明页误当成业务数据表
     */
    private boolean isSqlDescriptionSheet(List<String> header) {
        List<String> cells = header.stream()
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::trim)
                .collect(Collectors.toList());
        if (cells.isEmpty() || cells.size() > 2) {
            return false;
        }
        String firstRowText = String.join(" ", cells).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int sqlScore = 0;
        String[] keywords = {"select ", " from ", " where ", " join ", " group by ", " order by ", " insert ", " update ", " delete ", " create ", " alter ", " with "};
        for (String keyword : keywords) {
            if (firstRowText.contains(keyword)) {
                sqlScore++;
            }
        }
        boolean startsWithSql = firstRowText.startsWith("select ")
                || firstRowText.startsWith("with ")
                || firstRowText.startsWith("insert ")
                || firstRowText.startsWith("update ")
                || firstRowText.startsWith("delete ")
                || firstRowText.startsWith("create ")
                || firstRowText.startsWith("alter ");
        return sqlScore >= 2 && (startsWithSql || firstRowText.length() > 80);
    }

    /**
     * 合并编辑请求和历史配置中的工作表，保留用户未在本次提交中触达的旧表
     */
    public static void mergeSheets(CoreDatasource requestDatasource, DatasourceDTO sourceData) {
        if (requestDatasource.getType().equalsIgnoreCase("Excel")) {
            List<ExcelSheetData> newSheets = JsonUtil.parseList(requestDatasource.getConfiguration(), sheets);
            List<String> tableNames = newSheets.stream().map(ExcelSheetData::getDisplayTableName).collect(Collectors.toList());
            List<ExcelSheetData> oldSheets = JsonUtil.parseList(sourceData.getConfiguration(), sheets);
            for (ExcelSheetData oldSheet : oldSheets) {
                if (!tableNames.contains(oldSheet.getDisplayTableName())) {
                    newSheets.add(oldSheet);
                }
            }
            requestDatasource.setConfiguration(JsonUtil.toJSONString(newSheets).toString());
        } else {
            ExcelConfiguration excelConfiguration = JsonUtil.parseObject(requestDatasource.getConfiguration(), ExcelConfiguration.class);
            List<ExcelSheetData> newSheets = excelConfiguration.getSheets();
            List<String> tableNames = newSheets.stream().map(ExcelSheetData::getDisplayTableName).collect(Collectors.toList());
            List<ExcelSheetData> oldSheets = JsonUtil.parseObject(sourceData.getConfiguration(), ExcelConfiguration.class).getSheets();
            for (ExcelSheetData oldSheet : oldSheets) {
                if (!tableNames.contains(oldSheet.getDisplayTableName())) {
                    newSheets.add(oldSheet);
                }
            }
            excelConfiguration.setSheets(newSheets);
            requestDatasource.setConfiguration(JsonUtil.toJSONString(excelConfiguration).toString());
        }

    }

    /**
     * 从数据源配置中提取可导入工作表，转换为数据集侧可选择的数据表描述
     */
    public static List<DatasetTableDTO> tables(DatasourceRequest datasourceRequest) throws CrestException {
        List<DatasetTableDTO> tableDescs = new ArrayList<>();
        try {
            String sheets = "";
            if (datasourceRequest.getDatasource().getType().equalsIgnoreCase("Excel")) {
                sheets = datasourceRequest.getDatasource().getConfiguration();
            } else {
                sheets = objectMapper.readTree(datasourceRequest.getDatasource().getConfiguration()).get("sheets").toString();
            }
            JsonNode rootNode = objectMapper.readTree(sheets);
            for (int i = 0; i < rootNode.size(); i++) {
                if (!isImportableSheet(rootNode.get(i))) {
                    continue;
                }
                DatasetTableDTO datasetTableDTO = new DatasetTableDTO();
                datasetTableDTO.setTableName(rootNode.get(i).get("displayTableName").asText());
                datasetTableDTO.setName(rootNode.get(i).get("displayTableName").asText());
                datasetTableDTO.setDatasourceId(datasourceRequest.getDatasource().getId());
                datasetTableDTO.setLastUpdateTime(rootNode.get(i).get("lastUpdateTime") == null ? datasourceRequest.getDatasource().getCreateTime() : rootNode.get(i).get("lastUpdateTime").asLong(0L));
                tableDescs.add(datasetTableDTO);
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }

        return tableDescs;
    }

    /**
     * 构建内部表名到展示表名的映射，兼容本地 Excel 和远程 Excel 的配置结构
     */
    public static Map<String, String> getTableNamesMap(String type, String configuration) throws CrestException {
        Map<String, String> result = new HashMap<>();
        JsonNode rootNode = null;
        // 历史配置可能未加密，解密失败后回退读取明文配置
        String sheets = configuration;
        try {
            if (type.equalsIgnoreCase("ExcelRemote")) {
                sheets = objectMapper.readTree(configuration).get("sheets").toString();
            }
            rootNode = objectMapper.readTree((String) EncryptUtils.aesDecrypt(sheets));
        } catch (Exception e) {
            try {
                rootNode = objectMapper.readTree(sheets);
            } catch (Exception ex) {
                CrestException.throwException(ex);
            }
        }
        if (rootNode != null) {
            for (int i = 0; i < rootNode.size(); i++) {
                if (!isImportableSheet(rootNode.get(i))) {
                    continue;
                }
                result.put(rootNode.get(i).get("tableName").asText(), rootNode.get(i).get("displayTableName").asText());
            }
        }
        return result;
    }

    /**
     * 判断配置节点是否允许作为数据表导入，缺省值按历史配置的可导入语义处理
     */
    private static boolean isImportableSheet(JsonNode sheetNode) {
        return sheetNode == null || sheetNode.get("sheet") == null || sheetNode.get("sheet").asBoolean(true);
    }

    /**
     * 读取数据源配置中的原始文件名，列表接口用它展示用户上传或远程解析的文件
     */
    public static String getFileName(CoreDatasource datasource) throws CrestException {
        if (datasource.getType().equalsIgnoreCase("ExcelRemote")) {
            ExcelConfiguration excelConfiguration = JsonUtil.parseObject(datasource.getConfiguration(), ExcelConfiguration.class);
            for (ExcelSheetData sheet : excelConfiguration.getSheets()) {
                return sheet.getFileName();
            }
        }
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree((String) EncryptUtils.aesDecrypt(datasource.getConfiguration()));
        } catch (Exception e) {
            try {
                rootNode = objectMapper.readTree(datasource.getConfiguration());
            } catch (Exception ex) {
                CrestException.throwException(ex);
            }
        }
        if (rootNode != null) {
            for (int i = 0; i < rootNode.size(); i++) {
                return rootNode.get(i).get("fileName").asText();
            }
        }
        return "";
    }

    /**
     * 读取配置中保存的文件大小文本；远程 Excel 从封装配置读取，本地 Excel 从工作表节点读取
     */
    public static String getSize(CoreDatasource datasource) throws CrestException {
        if (datasource.getType().equalsIgnoreCase("ExcelRemote")) {
            ExcelConfiguration excelConfiguration = JsonUtil.parseObject(datasource.getConfiguration(), ExcelConfiguration.class);
            for (ExcelSheetData sheet : excelConfiguration.getSheets()) {
                return sheet.getSize();
            }
        }
        try {
            JsonNode rootNode = objectMapper.readTree(datasource.getConfiguration());
            for (int i = 0; i < rootNode.size(); i++) {
                return rootNode.get(i).get("size").asText();
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return "0 B";
    }

    /**
     * 读取指定工作表的数据行。远程文件会先下载到临时目录，本地文件直接从配置路径读取
     */
    public List<String[]> fetchDataList(DatasourceRequest datasourceRequest) throws CrestException, IOException {
        List<String[]> dataList = new ArrayList<>();
        if (datasourceRequest.getDatasource().getType().equalsIgnoreCase("ExcelRemote")) {
            ExcelConfiguration excelConfiguration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), ExcelConfiguration.class);
            Map<String, String> fileNames = downLoadRemoteExcel(excelConfiguration);
            for (ExcelSheetData sheet : excelConfiguration.getSheets()) {
                if (sheet.getDisplayTableName().equalsIgnoreCase(datasourceRequest.getTable())) {
                    List<TableField> tableFields = sheet.getFields();
                    String suffix = fileNames.get("fileName").substring(fileNames.get("fileName").lastIndexOf(".") + 1);
                    try (InputStream inputStream = newExcelInputStream(excelTempFile(fileNames.get("tranName")))) {
                        if (Strings.CI.equals(suffix, "csv")) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                                // 跳过 CSV 表头，后续只读取数据行
                                reader.readLine();
                                dataList = csvData(reader, false, tableFields.size());
                            }
                        } else {
                            dataList = fetchExcelDataList(sheet.getTableName(), inputStream);
                        }
                    }
                }
            }
            if (StringUtils.isNotEmpty(fileNames.get("tranName"))) {
                deleteExcelTempFile(fileNames.get("tranName"));
            }
        } else {
            try {
                JsonNode rootNode = objectMapper.readTree(datasourceRequest.getDatasource().getConfiguration());
                for (int i = 0; i < rootNode.size(); i++) {
                    if (rootNode.get(i).get("displayTableName").asText().equalsIgnoreCase(datasourceRequest.getTable())) {
                        List<TableField> tableFields = JsonUtil.parseList(rootNode.get(i).get("fields").toString(), TableFieldListTypeReference);
                        String suffix = rootNode.get(i).get("path").asText().substring(rootNode.get(i).get("path").asText().lastIndexOf(".") + 1);
                        try (InputStream inputStream = newExcelInputStream(excelStoredFile(rootNode.get(i).get("path").asText()))) {
                            if (Strings.CI.equals(suffix, "csv")) {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                                    // 跳过 CSV 表头，后续只读取数据行
                                    reader.readLine();
                                    dataList = csvData(reader, false, tableFields.size());
                                }
                            } else {
                                dataList = fetchExcelDataList(rootNode.get(i).get("tableName").asText(), inputStream);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                CrestException.throwException(e);
            }
        }
        return dataList;
    }

    /**
     * 从 Excel 工作簿中读取指定工作表的数据行，工作表名称按用户配置匹配
     */
    private List<String[]> fetchExcelDataList(String sheetName, InputStream inputStream) {
        NoModelDataListener noModelDataListener = new NoModelDataListener();
        ExcelReader excelReader = EasyExcel.read(inputStream, noModelDataListener).build();
        List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
        for (ReadSheet readSheet : sheets) {
            if (!sheetName.equalsIgnoreCase(readSheet.getSheetName())) {
                continue;
            }
            noModelDataListener.clear();
            List<TableField> fields = new ArrayList<>();
            excelReader.read(readSheet);
            for (TableField tableFiled : excelTableFields(noModelDataListener.getHeader())) {
                tableFiled.setNativeType("TEXT");
                fields.add(tableFiled);
            }
        }
        return noModelDataListener.getData();
    }

    /**
     * 返回指定工作表保存的字段配置，并补齐旧版本配置中缺失的字段类型信息
     */
    public static List<TableField> getTableFields(DatasourceRequest datasourceRequest) throws CrestException {
        List<TableField> tableFields = new ArrayList<>();
        TypeReference<List<TableField>> listTypeReference = new TypeReference<List<TableField>>() {
        };
        try {
            String sheets = "";
            if (datasourceRequest.getDatasource().getType().equalsIgnoreCase("Excel")) {
                sheets = datasourceRequest.getDatasource().getConfiguration();
            } else {
                sheets = objectMapper.readTree(datasourceRequest.getDatasource().getConfiguration()).get("sheets").toString();
            }
            JsonNode rootNode = objectMapper.readTree(sheets);
            for (int i = 0; i < rootNode.size(); i++) {
                if (rootNode.get(i).get("displayTableName").asText().equalsIgnoreCase(datasourceRequest.getTable())) {
                    tableFields = JsonUtil.parseList(rootNode.get(i).get("fields").toString(), listTypeReference);
                    tableFields.forEach(ExcelUtils::normalizeExcelTableField);
                }
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return tableFields;
    }

    /**
     * 保存用户上传文件并解析预览结构，返回文件元数据、工作表清单和字段定义
     */
    public ExcelFileData excelSaveAndParse(MultipartFile file, String createBy) throws CrestException {
        String filename = file.getOriginalFilename();
        FileUtils.validateUploadFilename(filename);
        List<ExcelSheetData> excelSheetDataList = null;
        try {
            excelSheetDataList = parseExcel(filename, file.getInputStream(), true, filename);
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        List<ExcelSheetData> returnSheetDataList = new ArrayList<>();
        returnSheetDataList = excelSheetDataList;
        // 先持久化原始文件，后续配置只保存生成的本地路径
        String excelId = UUID.randomUUID().toString();
        String filePath = saveFile(file, excelId);

        for (ExcelSheetData excelSheetData : returnSheetDataList) {
            excelSheetData.setLastUpdateTime(System.currentTimeMillis());
            excelSheetData.setTableName(excelSheetData.getExcelLabel());
            excelSheetData.setDisplayTableName("excel_" + excelSheetData.getExcelLabel() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            excelSheetData.setPath(filePath);
            excelSheetData.setSheetId(UUID.randomUUID().toString());
            excelSheetData.setSheetExcelId(excelId);
            excelSheetData.setFileName(filename);
            for (TableField field : excelSheetData.getFields()) {
                normalizeExcelTableField(field);
            }
            long size = 0;
            String unit = "B";
            if (file.getSize() / 1024 == 0) {
                size = file.getSize();
            }
            if (0 < file.getSize() / 1024 && file.getSize() / 1024 < 1024) {
                size = file.getSize() / 1024;
                unit = "KB";
            }
            if (1024 <= file.getSize() / 1024) {
                size = file.getSize() / 1024 / 1024;
                unit = "MB";
            }
            excelSheetData.setSize(size + " " + unit);
        }
        ExcelFileData excelFileData = new ExcelFileData();
        excelFileData.setExcelLabel(filename.substring(0, filename.lastIndexOf('.')));
        excelFileData.setId(excelId);
        excelFileData.setPath(filePath);
        excelFileData.setSheets(returnSheetDataList);
        return excelFileData;
    }

    /**
     * 通过下载远程文件验证远程 Excel 配置可用性，验证结束后清理临时文件
     */
    public static String checkStatus(DatasourceRequest datasourceRequest) throws FileNotFoundException {
        ExcelConfiguration excelConfiguration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), ExcelConfiguration.class);
        Map<String, String> fileNames = new HashMap<>();
        try {
            fileNames = downLoadRemoteExcel(excelConfiguration);
            return "Success";
        } catch (Exception e) {
            throw e;
        } finally {
            if (StringUtils.isNotEmpty(fileNames.get("tranName"))) {
                deleteExcelTempFile(fileNames.get("tranName"));
            }
        }
    }

    /**
     * 下载并解析远程 Excel 文件，返回与本地上传一致的预览数据结构
     */
    @SuppressWarnings("java/path-injection")
    public ExcelFileData parseRemoteExcel(RemoteExcelRequest remoteExcelRequest) throws CrestException, FileNotFoundException {
        Map<String, String> fileNames = downLoadRemoteExcel(remoteExcelRequest);
        List<ExcelSheetData> returnSheetDataList = new ArrayList<>();
        try (InputStream fileInputStream = newExcelInputStream(excelTempFile(fileNames.get("tranName")))) {
            returnSheetDataList = parseExcel(fileNames.get("tranName"), fileInputStream, true, fileNames.get("fileName"));
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        for (ExcelSheetData excelSheetData : returnSheetDataList) {
            excelSheetData.setLastUpdateTime(System.currentTimeMillis());
            excelSheetData.setTableName(excelSheetData.getExcelLabel());
            excelSheetData.setDisplayTableName("excel_" + excelSheetData.getExcelLabel() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            excelSheetData.setPath(getExcelPath() + fileNames.get("tranName"));
            excelSheetData.setSheetId(UUID.randomUUID().toString());
            excelSheetData.setSheetExcelId(fileNames.get("tranName").split("\\.")[0]);
            excelSheetData.setFileName(fileNames.get("fileName"));
            for (TableField field : excelSheetData.getFields()) {
                normalizeExcelTableField(field);
            }
            long size = 0;
            File file = excelTempFile(fileNames.get("tranName"));
            String unit = "B";
            if (file.length() / 1024 == 0) {
                size = file.length();
            }
            if (0 < file.length() / 1024 && file.length() / 1024 < 1024) {
                size = file.length() / 1024;
                unit = "KB";
            }
            if (1024 <= file.length() / 1024) {
                size = file.length() / 1024 / 1024;
                unit = "MB";
            }
            excelSheetData.setSize(size + " " + unit);
        }

        ExcelFileData excelFileData = new ExcelFileData();
        excelFileData.setExcelLabel(fileNames.get("fileName").split("\\.")[0]);
        excelFileData.setId(fileNames.get("tranName").split("\\.")[0]);
        excelFileData.setPath(excelTempFile(fileNames.get("tranName")).getPath());
        excelFileData.setSheets(returnSheetDataList);
        if (StringUtils.isNotEmpty(fileNames.get("tranName"))) {
            deleteExcelTempFile(fileNames.get("tranName"));
        }
        return excelFileData;
    }

    /**
     * 按远程地址协议选择下载方式，HTTP 和 FTP 下载都会返回原文件名与临时文件名
     */
    private static Map<String, String> downLoadRemoteExcel(ExcelConfiguration remoteExcelRequest) throws CrestException, FileNotFoundException {
        Map<String, String> fileNames = new HashMap<>();
        if (remoteExcelRequest.getUrl().trim().startsWith("http")) {
            HttpClientConfig httpClientConfig = new HttpClientConfig();
            if (StringUtils.isNotEmpty(remoteExcelRequest.getUserName()) && StringUtils.isNotEmpty(remoteExcelRequest.getPasswd())) {
                String authValue = "Basic " + Base64.getUrlEncoder().encodeToString((remoteExcelRequest.getUserName() + ":" + remoteExcelRequest.getPasswd()).getBytes());
                httpClientConfig.addHeader("Authorization", authValue);
            }
            ensureExcelDirectory();
            fileNames = HttpClientUtil.downloadFile(remoteExcelRequest.getUrl(), httpClientConfig, ExcelUtils::writeDownloadedExcelFile);
        } else if (remoteExcelRequest.getUrl().trim().startsWith("ftp")) {
            fileNames = downLoadFromFtp(remoteExcelRequest);
        } else {
            CrestException.throwException(Translator.get("i18n_unsupported_protocol"));
        }
        return fileNames;
    }

    /**
     * 将 HTTP 远程下载结果写入 Excel 共享目录。
     */
    private static void writeDownloadedExcelFile(String tranName, InputStream inputStream) throws IOException {
        try (OutputStream outputStream = newExcelOutputStream(excelTempFile(tranName))) {
            copyExcelStream(inputStream, outputStream);
        }
    }

    /**
     * 确保 Excel 根目录存在，所有上传和远程下载文件都落在该目录下
     */
    private static File ensureExcelDirectory() {
        try {
            return storageService().ensureDirectory(getExcelPath());
        } catch (Exception e) {
            CrestException.throwException("Excel文件目录创建失败: " + getExcelPath());
            return null;
        }
    }

    /**
     * 保存上传文件到受控目录，文件名使用生成标识和原始扩展名，避免暴露用户文件名为路径
     */
    private static String saveFile(MultipartFile file, String fileNameUUID) throws CrestException {
        String filePath = null;
        try {
            String filename = file.getOriginalFilename();
            FileUtils.validateUploadFilename(filename);
            String suffix = filename.substring(filename.lastIndexOf(".") + 1);
            ensureExcelDirectory();
            File f = excelTempFile(fileNameUUID + "." + suffix);
            filePath = f.getPath();
            try (OutputStream fileOutputStream = newExcelOutputStream(f)) {
                fileOutputStream.write(file.getBytes());
                fileOutputStream.flush();
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return filePath;
    }

    /**
     * 判断 CSV 行是否没有有效单元格内容，用于读取数据时跳过空行
     */
    private static boolean isEmpty(List<String> cells) {
        if (CollectionUtils.isEmpty(cells)) {
            return true;
        }
        boolean isEmpty = true;
        for (int i = 0; i < cells.size(); i++) {
            if (isEmpty && StringUtils.isEmpty(cells.get(i))) {
                isEmpty = true;
            } else {
                isEmpty = false;
            }
        }
        return isEmpty;
    }

    /**
     * 解析 CSV 数据行，按字段数量截断多余列，并把约定的空值占位符转成空值
     */
    public static List<String[]> csvData(BufferedReader reader, boolean isPreview, int size) throws CrestException {
        List<String[]> data = new ArrayList<>();
        try {
            int num = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> cells = parseCsvLine(line);
                if (!isEmpty(cells)) {
                    if (cells.size() > size) {
                        cells = cells.subList(0, size);
                    }
                    cells = cells.stream().map(ExcelUtils::normalizeNullPlaceholder).collect(Collectors.toList());
                    data.add(cells.toArray(new String[]{}));
                    num++;
                }
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return data;
    }

    /**
     * 按 CSV 引号规则拆分单行文本，支持双引号转义和逗号内容
     */
    private static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /**
     * 根据文本内容推断基础字段类型，长数字、前导零和非数字内容统一保留为文本
     */
    private String cellType(String value) {
        String text = StringUtils.trimToEmpty(value);
        if (StringUtils.isEmpty(text) || text.length() > 19 || hasLeadingZeroNumericText(text)) {
            return "TEXT";
        }
        String regex = "^-?\\d+(\\.\\d+)?$";
        if (!text.matches(regex)) {
            return "TEXT";
        }
        try {
            Double d = Double.valueOf(text);
            double eps = 1e-10;
            if (d - Math.floor(d) < eps) {
                if (text.contains(".")) {
                    return "DOUBLE";
                }
                return "LONG";
            } else {
                return "DOUBLE";
            }
        } catch (Exception e2) {
            return "TEXT";
        }
    }

    /**
     * 使用预览数据逐行修正字段类型，遇到文本值时优先保证后续导入不丢失格式
     */
    private void cellType(String value, int i, TableField tableFiled) {
        if (StringUtils.isBlank(value) || isNullPlaceholder(value)) {
            return;
        }
        if (shouldKeepExcelFieldAsText(tableFiled)) {
            tableFiled.setNativeType(DEFAULT_EXCEL_FIELD_TYPE);
            return;
        }
        String type = cellType(value);
        if (shouldPreferExcelFieldAsDouble(tableFiled) && isExcelNumericType(type)) {
            type = "DOUBLE";
        }
        if (i == 0) {
            tableFiled.setNativeType(type);
        } else {
            if (tableFiled.getNativeType() == null) {
                tableFiled.setNativeType(type);
            } else {
                if (type.equalsIgnoreCase("TEXT")) {
                    tableFiled.setNativeType(type);
                }
                if (type.equalsIgnoreCase("DOUBLE") && tableFiled.getNativeType().equalsIgnoreCase("LONG")) {
                    tableFiled.setNativeType(type);
                }
            }
        }

    }

    /**
     * 判断字段名是否更适合使用小数类型，避免金额、均值等列被首批整数样本误判为长整型
     */
    private boolean shouldPreferExcelFieldAsDouble(TableField tableField) {
        if (tableField == null) {
            return false;
        }
        String fieldName = StringUtils.defaultString(StringUtils.firstNonBlank(tableField.getName(), tableField.getOriginName()));
        return containsAny(fieldName, "工作量", "金额", "均值", "平均", "阈值");
    }

    /**
     * 判断推断类型是否属于 Excel 导入支持的数值类型
     */
    private boolean isExcelNumericType(String type) {
        return Strings.CI.equals(type, "LONG") || Strings.CI.equals(type, "DOUBLE");
    }

    /**
     * 判断数字文本是否带前导零，此类内容通常是编码或账号，必须按文本保留
     */
    private boolean hasLeadingZeroNumericText(String value) {
        String text = StringUtils.trimToEmpty(value);
        int start = text.startsWith("-") ? 1 : 0;
        return text.length() > start + 1 && text.charAt(start) == '0' && Character.isDigit(text.charAt(start + 1));
    }

    /**
     * 根据字段名识别编码、日期、标识符等敏感格式列，这些列不参与数值类型自动收敛
     */
    private boolean shouldKeepExcelFieldAsText(TableField tableField) {
        if (tableField == null) {
            return false;
        }
        String fieldName = StringUtils.defaultString(StringUtils.firstNonBlank(tableField.getName(), tableField.getOriginName()));
        String lowerName = fieldName.toLowerCase(Locale.ROOT);
        return containsAny(fieldName, "编号", "编码", "代码", "单号", "账号", "帐号", "卡号", "身份证", "版本号", "序号")
                || containsAny(fieldName, "日期", "时间", "月份", "年月")
                || hasEnglishIdentifierToken(lowerName, "id")
                || hasEnglishIdentifierToken(lowerName, "code");
    }

    /**
     * 判断字段名是否包含任一业务关键词，用于字段类型推断的名称规则
     */
    private boolean containsAny(String value, String... keywords) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断英文标识符是否作为独立词出现，避免把普通单词中的片段误判为编码字段
     */
    private boolean hasEnglishIdentifierToken(String lowerName, String token) {
        if (StringUtils.isBlank(lowerName)) {
            return false;
        }
        if (lowerName.equals(token)) {
            return true;
        }
        return lowerName.matches(".*(^|[^a-z0-9])" + token + "($|[^a-z0-9]).*")
                || lowerName.matches(".*[\\u4e00-\\u9fa5]" + token + "$");
    }

    /**
     * 规范化 Excel 单元格值，把约定空值占位符转为空值
     */
    private static String normalizeExcelCellValue(String value) {
        if (StringUtils.isEmpty(value) || isNullPlaceholder(value)) {
            return null;
        }
        return value;
    }

    /**
     * 规范化 CSV 单元格值，仅替换约定空值占位符，保留其他原始文本
     */
    private static String normalizeNullPlaceholder(String value) {
        if (isNullPlaceholder(value)) {
            return null;
        }
        return value;
    }

    /**
     * 识别导入文件中表示数据库空值的占位符
     */
    private static boolean isNullPlaceholder(String value) {
        String text = StringUtils.trimToEmpty(value);
        return Strings.CI.equals(text, "null") || Strings.CS.equals(text, "\\N");
    }

    /**
     * EasyExcel 无模型读取监听器，按表头列顺序收集数据行，保证空列不会导致列偏移
     */
    @Data
    @EqualsAndHashCode(callSuper = false)
    public class NoModelDataListener extends AnalysisEventListener<Map<Integer, String>> {
        private List<String[]> data = new ArrayList<>();
        private List<String> header = new ArrayList<>();
        private List<Integer> headerKey = new ArrayList<>();

        /**
         * 读取表头并记录最小列到最大列的完整列序号，保留中间空列位置
         */
        @Override
        public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
            super.invokeHead(headMap, context);
            if (headMap.isEmpty()) {
                return;
            }
            int minKey = Collections.min(headMap.keySet());
            int maxKey = Collections.max(headMap.keySet());
            for (int key = minKey; key <= maxKey; key++) {
                String value = headCellValue(headMap.get(key));
                headerKey.add(key);
                header.add(value);
            }
        }

        /**
         * 将表头单元格转换为文本，只接受字符串和数字类型作为可用表头
         */
        private String headCellValue(ReadCellData<?> cellData) {
            if (cellData == null) {
                return null;
            }
            if (cellData.getType().equals(CellDataTypeEnum.STRING)) {
                return cellData.getStringValue();
            }
            if (cellData.getType().equals(CellDataTypeEnum.NUMBER)) {
                return cellData.getNumberValue().toString();
            }
            return null;
        }

        /**
         * 按表头列序号读取一行数据，缺失列用空值占位，保持字段和数据列对齐
         */
        @Override
        public void invoke(Map<Integer, String> dataMap, AnalysisContext context) {
            List<String> line = new ArrayList<>();
            for (Integer key : headerKey) {
                line.add(normalizeExcelCellValue(dataMap.get(key)));
            }
            data.add(line.toArray(new String[line.size()]));
        }

        /**
         * EasyExcel 回调入口保留为空，数据收集在逐行回调中完成
         */
        @Override
        public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        }

        /**
         * 复用监听器读取下一个工作表前清空当前工作表的表头和数据缓存
         */
        public void clear() {
            data.clear();
            header.clear();
            headerKey.clear();
        }
    }

    /**
     * 工作表导入检查结果，封装是否导入、状态码和给用户展示的跳过原因
     */
    @Data
    private static class ExcelSheetInspection {
        private boolean importable;
        private String status;
        private String message;

        /**
         * 创建可导入工作表的检查结果
         */
        private static ExcelSheetInspection normal() {
            ExcelSheetInspection inspection = new ExcelSheetInspection();
            inspection.setImportable(true);
            inspection.setStatus(SHEET_STATUS_NORMAL);
            inspection.setMessage("");
            return inspection;
        }

        /**
         * 创建跳过导入的工作表检查结果，并保留可展示的原因
         */
        private static ExcelSheetInspection skipped(String message) {
            ExcelSheetInspection inspection = new ExcelSheetInspection();
            inspection.setImportable(false);
            inspection.setStatus(SHEET_STATUS_SKIPPED);
            inspection.setMessage(message);
            return inspection;
        }
    }


    /**
     * 解析 Excel 或 CSV 输入流，生成工作表字段、预览数据和面向前端的 JSON 行结构
     */
    private List<ExcelSheetData> parseExcel(String filename, InputStream inputStream, boolean isPreview, String originFilename) throws IOException {
        List<ExcelSheetData> excelSheetDataList = new ArrayList<>();
        String suffix = filename.substring(filename.lastIndexOf(".") + 1);
        if (Strings.CI.equals(suffix, "xlsx") || Strings.CI.equals(suffix, "xls")) {
            NoModelDataListener noModelDataListener = new NoModelDataListener();
            ExcelReader excelReader = EasyExcel.read(inputStream, noModelDataListener).build();
            List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
            for (ReadSheet readSheet : sheets) {
                noModelDataListener.clear();
                List<TableField> fields = new ArrayList<>();
                excelReader.read(readSheet);
                List<String[]> data = new ArrayList<>(noModelDataListener.getData());
                ExcelSheetData excelSheetData = new ExcelSheetData();
                excelSheetData.setFileName(filename);
                excelSheetData.setExcelLabel(readSheet.getSheetName());
                ExcelSheetInspection inspection = inspectSheet(noModelDataListener.getHeader());
                excelSheetData.setSheet(inspection.isImportable());
                excelSheetData.setInspectionStatus(inspection.getStatus());
                excelSheetData.setInspectionMessage(inspection.getMessage());
                if (!inspection.isImportable()) {
                    excelSheetData.setFields(fields);
                    excelSheetData.setData(new ArrayList<>());
                    excelSheetDataList.add(excelSheetData);
                    continue;
                }
                for (TableField tableFiled : excelTableFields(noModelDataListener.getHeader())) {
                    tableFiled.setNativeType(null);
                    tableFiled.setChecked(true);
                    fields.add(tableFiled);
                }
                if (isPreview) {
                    for (int i = 0; i < data.size(); i++) {
                        for (int j = 0; j < data.get(i).length; j++) {
                            if (j < fields.size()) {
                                cellType(data.get(i)[j], i, fields.get(j));
                            }
                        }
                    }
                    if (data.size() > 100) {
                        data = data.subList(0, 100);
                    }
                }

                for (int i = 0; i < fields.size(); i++) {
                    if (StringUtils.isEmpty(fields.get(i).getNativeType())) {
                        fields.get(i).setNativeType(DEFAULT_EXCEL_FIELD_TYPE);
                    }
                    normalizeExcelTableField(fields.get(i));
                }

                excelSheetData.setFields(fields);
                excelSheetData.setData(data);
                excelSheetDataList.add(excelSheetData);
            }
        }

        if (Strings.CI.equals(suffix, "csv")) {
            List<TableField> fields = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            // 读取 CSV 首行作为表头，后续数据行由 csvData 统一解析
            String s = reader.readLine();
            List<String> header = new ArrayList<>();
            if (StringUtils.isNotEmpty(s)) {
                List<String> split = parseCsvLine(s);
                for (String filedName : split) {
                    if (filedName.startsWith(UFEFF)) {
                        filedName = filedName.replace(UFEFF, "");
                    }
                    header.add(filedName);
                }
            }

            List<String[]> data = csvData(reader, isPreview, header.size());
            ExcelSheetInspection inspection = inspectSheet(header);
            if (inspection.isImportable()) {
                for (TableField tableFiled : excelTableFields(header)) {
                    tableFiled.setNativeType(null);
                    tableFiled.setChecked(true);
                    fields.add(tableFiled);
                }
            } else {
                data = new ArrayList<>();
            }
            if (isPreview) {
                for (int i = 0; i < data.size(); i++) {
                    for (int j = 0; j < data.get(i).length; j++) {
                        if (j < fields.size()) {
                            cellType(data.get(i)[j], i, fields.get(j));
                        }
                    }
                }
                if (data.size() > 100) {
                    data = data.subList(0, 100);
                }
            }
            for (int i = 0; i < fields.size(); i++) {
                if (StringUtils.isEmpty(fields.get(i).getNativeType())) {
                    fields.get(i).setNativeType(DEFAULT_EXCEL_FIELD_TYPE);
                }
                normalizeExcelTableField(fields.get(i));
            }

            ExcelSheetData excelSheetData = new ExcelSheetData();
            String[] fieldArray = fields.stream().map(TableField::getName).toArray(String[]::new);
            excelSheetData.setFields(fields);
            excelSheetData.setData(data);
            excelSheetData.setFileName(filename);
            excelSheetData.setExcelLabel(originFilename.substring(0, originFilename.lastIndexOf('.')));
            excelSheetData.setSheet(inspection.isImportable());
            excelSheetData.setInspectionStatus(inspection.getStatus());
            excelSheetData.setInspectionMessage(inspection.getMessage());
            excelSheetDataList.add(excelSheetData);
        }
        inputStream.close();

        for (ExcelSheetData excelSheetData : excelSheetDataList) {
            List<String[]> data = excelSheetData.getData();
            String[] fieldArray = excelSheetData.getFields().stream().map(TableField::getName).toArray(String[]::new);

            List<Map<String, Object>> jsonArray = new ArrayList<>();
            if (data != null) {
                jsonArray = data.stream().map(ele -> {
                    Map<String, Object> map = new HashMap<>();
                    for (int i = 0; i < fieldArray.length; i++) {
                        map.put(fieldArray[i], i < ele.length ? ele[i] : "");
                    }
                    return map;
                }).collect(Collectors.toList());
            }
            excelSheetData.setJsonArray(jsonArray);
        }

        return excelSheetDataList;
    }

    /**
     * 从 FTP 地址下载远程文件到临时目录，下载前校验协议、主机和文件扩展名
     */
    @SuppressWarnings("java/ssrf")
    public static Map<String, String> downLoadFromFtp(ExcelConfiguration remoteExcelRequest) {
        Map<String, String> fileNames = new HashMap<>();
        String username = "";
        String password = "";
        String serverAddress = "";
        String filePath = "";
        try {
            URI uri = URI.create(remoteExcelRequest.getUrl());
            if (!Strings.CI.equals(uri.getScheme(), "ftp") || StringUtils.isBlank(uri.getHost())) {
                CrestException.throwException(Translator.get("i18n_invalid_address"));
            }
            serverAddress = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
            filePath = StringUtils.defaultString(uri.getPath());
            if (StringUtils.isNotBlank(uri.getRawUserInfo())) {
                String userInfo = uri.getRawUserInfo();
                int separator = userInfo.indexOf(':');
                if (separator > -1) {
                    username = URLDecoder.decode(userInfo.substring(0, separator), StandardCharsets.UTF_8);
                    password = URLDecoder.decode(userInfo.substring(separator + 1), StandardCharsets.UTF_8);
                } else {
                    username = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            CrestException.throwException(Translator.get("i18n_invalid_address"));
        }
        if (StringUtils.isNotEmpty(remoteExcelRequest.getUserName())) {
            username = remoteExcelRequest.getUserName();
        }
        if (StringUtils.isNotEmpty(remoteExcelRequest.getPasswd())) {
            password = remoteExcelRequest.getPasswd();
        }
        filePath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String suffix = filePath.substring(filePath.lastIndexOf(".") + 1);
        if (!Arrays.asList("csv", "xlsx", "xls").contains(suffix)) {
            CrestException.throwException(Translator.get("i18n_unsupported_file_format"));
        }
        String tranName = UUID.randomUUID().toString() + "." + suffix;
        File localFile = excelTempFile(tranName);
        fileNames.put("fileName", filePath);
        fileNames.put("tranName", tranName);

        try {
            URL url;
            HttpClientUtil.assertRemoteHostAllowed(serverAddress);
            if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
                url = new URL("ftp://" + username + ":" + URLEncoder.encode(password, StandardCharsets.UTF_8) + "@" + serverAddress + "/" + filePath);
            } else {
                url = new URL("ftp://" + serverAddress + "/" + filePath);
            }

            URLConnection conn = url.openConnection();
            try (InputStream inputStream = conn.getInputStream();
                 OutputStream outputStream = newExcelOutputStream(localFile)) {
                copyExcelStream(inputStream, outputStream);
            }

        } catch (IOException e) {
            CrestException.throwException(Translator.get("i18n_file_download_failed") + ", " + e.getMessage());
        }
        return fileNames;
    }

    /**
     * 流式复制 Excel 文件内容，避免大文件占用过多堆内存。
     */
    private static void copyExcelStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

}
