package io.crest.chart.server;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.chart.ChartDataApi;
import io.crest.api.chart.dto.ViewDetailField;
import io.crest.api.chart.request.ChartExcelRequest;
import io.crest.api.chart.request.ChartExcelRequestInner;
import io.crest.auth.CrestLinkPermit;
import io.crest.chart.constant.ChartConstants;
import io.crest.chart.manage.ChartDataManage;
import io.crest.constant.*;
import io.crest.dataset.manage.PermissionManage;
import io.crest.dataset.server.DatasetFieldServer;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.exception.CrestException;
import io.crest.exportCenter.dao.auto.entity.CoreExportTask;
import io.crest.exportCenter.manage.ExportCenterDownLoadManage;
import io.crest.exportCenter.manage.ExportCenterManage;
import io.crest.exportCenter.util.ExportCenterUtils;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.view.dto.*;
import io.crest.i18n.Lang;
import io.crest.i18n.Translator;
import io.crest.log.CrestAudit;
import io.crest.result.ResultCode;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.JsonUtil;
import io.crest.utils.LogUtil;
import io.crest.visualization.manage.VisualizationTemplateExtendDataManage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 图表数据查询、明细导出和导出表头渲染的服务入口
 */
@RestController
@RequestMapping("/chart-data")
@SuppressWarnings("unchecked")
public class ChartDataServer implements ChartDataApi {
    @Resource
    private ChartDataManage chartDataManage;
    @Resource
    private ExportCenterManage exportCenterManage;

    @Resource
    private VisualizationTemplateExtendDataManage extendDataManage;

    @Resource
    private PermissionManage permissionManage;
    @Resource
    private DatasetFieldServer datasetFieldServer;

    @Value("${crest.export.page.size:50000}")
    private Integer extractPageSize;
    private final Long sheetLimit = 1000000L;
    private static final String AUXILIARY_HEADER_GROUP_PREFIX = "__crest_aux_header_group__";


    /**
     * 获取图表数据，并在模板数据和数据集实时计算之间分流
     */
    @CrestLinkPermit("#p0.sceneId")
    @Override
    public ChartViewDTO getData(ChartViewDTO chartViewDTO) throws Exception {
        try {
            // 模板来源的数据直接读取扩展数据，普通数据集视图走实时计算
            if (CommonConstants.VIEW_DATA_FROM.TEMPLATE.equalsIgnoreCase(chartViewDTO.getDataFrom())) {
                return extendDataManage.getChartDataInfo(chartViewDTO.getId(), chartViewDTO);
            } else {
                DatasetUtils.viewDecode(chartViewDTO);
                ChartViewDTO dto = chartDataManage.calcData(chartViewDTO);
                DatasetUtils.viewEncode(dto);
                chartDataManage.encodeData(dto);
                return dto;
            }
        } catch (CrestException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.error("Chart data query failed: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), e);
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_fetch_error"));
        }
        return null;
    }

    /**
     * 查询 Excel 导出所需的图表明细数据，并填充导出请求上下文
     */
    public ChartViewDTO findExcelData(ChartExcelRequest request) {
        ChartViewDTO chartViewInfo = new ChartViewDTO();
        try {
            ChartViewDTO viewDTO = request.getViewInfo();
            viewDTO.setIsExcelExport(true);
            String[] dsHeader = null;
            Integer[] dsTypes = null;
            // 数据集原始导出复用明细表图表的导出结构
            if ("dataset".equals(request.getDownloadType())) {
                viewDTO.setExportDatasetOriginData(true);
                viewDTO.setResultMode(ChartConstants.VIEW_RESULT_MODE.ALL);
                viewDTO.setType("table-info");
                viewDTO.setRender("antv");
                List<DatasetTableFieldDTO> sourceFields = datasetFieldServer.listByDatasetGroup(viewDTO.getTableId());
                List<String> fileNames = permissionManage.filterColumnPermissions(sourceFields, new HashMap<>(), viewDTO.getTableId(), null).stream().map(DatasetTableFieldDTO::getEngineFieldName).collect(Collectors.toList());
                sourceFields = sourceFields.stream().filter(datasetTableFieldDTO -> fileNames.contains(datasetTableFieldDTO.getEngineFieldName())).collect(Collectors.toList());
                dsHeader = sourceFields.stream().map(DatasetTableFieldDTO::getName).toArray(String[]::new);
                dsTypes = sourceFields.stream().map(DatasetTableFieldDTO::getFieldType).toArray(Integer[]::new);
                TypeReference<List<ChartViewFieldDTO>> listTypeReference = new TypeReference<List<ChartViewFieldDTO>>() {
                };
                viewDTO.setXAxis(JsonUtil.parseList(JsonUtil.toJSONString(sourceFields).toString(), listTypeReference));
                viewDTO.getXAxis().forEach(x -> {
                    if (x.getOrderChecked()) {
                        x.setSort("asc");
                    }
                });
            }
            int curLimit = Math.toIntExact(ExportCenterUtils.getExportLimit("view"));
            int curDsLimit = Math.toIntExact(ExportCenterUtils.getExportLimit("dataset"));
            int viewLimit = Math.min(curLimit, curDsLimit);
            if (ChartConstants.VIEW_RESULT_MODE.CUSTOM.equals(viewDTO.getResultMode())) {
                Integer limitCount = viewDTO.getResultCount();
                viewDTO.setResultCount(Math.min(viewLimit, limitCount));
            } else {
                viewDTO.setResultCount(viewLimit);
            }
            if (CommonConstants.VIEW_DATA_FROM.TEMPLATE.equalsIgnoreCase(viewDTO.getDataFrom())) {
                chartViewInfo = extendDataManage.getChartDataInfo(viewDTO.getId(), viewDTO);
            } else {
                // 非模板数据按明细表逻辑计算导出结果
                viewDTO.setIsPlugin(false);
                chartViewInfo = chartDataManage.calcData(viewDTO);
            }
            List<Object[]> tableRow = (List) chartViewInfo.getData().get("sourceData");
            if ("dataset".equals(request.getDownloadType())) {
                request.setHeader(dsHeader);
                request.setExcelTypes(dsTypes);
            }
            request.setDetails(tableRow);
            request.setData(chartViewInfo.getData());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return chartViewInfo;
    }


    /**
     * 按图表格式化配置把数值转换为展示文本
     */
    public static String valueFormatter(BigDecimal value, FormatterCfgDTO formatter) {
        if (value == null) {
            return null;
        }
        String result;
        if (formatter.getType().equals("auto")) {
            result = transSeparatorAndSuffix(String.valueOf(transUnit(value, formatter)), formatter);
        } else if (formatter.getType().equals("value")) {
            result = transSeparatorAndSuffix(transDecimal(transUnit(value, formatter), formatter), formatter);
        } else if (formatter.getType().equals("percent")) {
            value = value.multiply(BigDecimal.valueOf(100));
            result = transSeparatorAndSuffix(transDecimal(value, formatter), formatter);
        } else {
            result = value.toString();
        }
        return result;
    }

    /**
     * 按配置单位缩放数值
     */
    private static BigDecimal transUnit(BigDecimal value, FormatterCfgDTO formatter) {
        return value.divide(BigDecimal.valueOf(formatter.getUnit()));
    }

    /**
     * 按小数位配置格式化数值
     */
    private static String transDecimal(BigDecimal value, FormatterCfgDTO formatter) {
        DecimalFormat df = new DecimalFormat("0." + new String(new char[formatter.getDecimalCount()]).replace('\0', '0'));
        return df.format(value);
    }

    /**
     * 为格式化后的数值追加千分位、单位和后缀
     */
    private static String transSeparatorAndSuffix(String value, FormatterCfgDTO formatter) {
        StringBuilder sb = new StringBuilder(value);

        if (formatter.getThousandSeparator()) {
            Pattern thousandsPattern = Pattern.compile("(\\d)(?=(\\d{3})+$)");
            String[] numArr = value.split("\\.");
            numArr[0] = addThousandSeparator(numArr[0], thousandsPattern);
            sb = new StringBuilder(String.join(".", numArr));
        }
        if (formatter.getType().equals("percent")) {
            sb.append('%');
        } else {
            switch (formatter.getUnit()) {
                case 1000:
                    sb.append("千");
                    break;
                case 10000:
                    sb.append("万");
                    break;
                case 1000000:
                    sb.append("百万");
                    break;
                case 100000000:
                    sb.append('亿');
                    break;
                default:
                    break;
            }
        }
        String suffix = formatter.getSuffix().trim();
        if (!suffix.isEmpty()) {
            if (suffix.equals("%")) {
                sb.append("\"%\"");
            } else {
                sb.append(suffix);
            }
        }
        return sb.toString();
    }


    /**
     * 为整数字符串添加千分位分隔符
     */
    private static String addThousandSeparator(String numStr, Pattern pattern) {
        Matcher matcher = pattern.matcher(numStr);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + ",");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    /**
     * 导出图表或数据集明细，支持直连下载和后台任务两种模式
     */
    @CrestLinkPermit("#p0.dvId")
    @Override
    public void innerExportDetails(ChartExcelRequest request, HttpServletResponse response) throws Exception {
        ensureExportRequest(request);
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String linkToken = httpServletRequest.getHeader(AuthConstant.LINK_TOKEN_KEY);
        LogUtil.info(request.getViewInfo().getId() + " " + StringUtils.isNotEmpty(linkToken) + " " + request.isEmbeddedExport());
        if (shouldWriteDirectExport(linkToken, request.isEmbeddedExport())) {
            OutputStream outputStream = response.getOutputStream();
            try {
                Workbook wb = new SXSSFWorkbook();
                // 构建导出表头的基础单元格样式
                CellStyle cellStyle = wb.createCellStyle();
                Font font = wb.createFont();
                // 表头字体保持统一字号
                font.setFontHeightInPoints((short) 12);
                // 表头字体使用加粗样式
                font.setBold(true);
                // 将字体配置写入单元格样式
                cellStyle.setFont(font);
                // 表头使用浅灰背景，便于和数据行区分
                cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                // 表头背景采用纯色填充
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                if ("dataset".equals(request.getDownloadType()) || request.getViewInfo().getType().equalsIgnoreCase("table-info") || request.getViewInfo().getType().equalsIgnoreCase("table-normal")) {
                    List<Object[]> details = new ArrayList<>();
                    Sheet detailsSheet;
                    Integer sheetIndex = 1;

                    boolean summaryEnabled = !"dataset".equals(request.getDownloadType()) && isSummaryEnabled(request.getViewInfo());
                    SummaryConfig summaryConfig = null;
                    SummaryAccumulator summaryAcc = null;
                    List<ChartViewFieldDTO> allExportColumns = null;
                    Map<String, BigDecimal> customSumResult = null;
                    if (summaryEnabled) {
                        summaryConfig = parseSummaryConfig(request.getViewInfo());
                        summaryAcc = new SummaryAccumulator();
                        allExportColumns = getAllExportColumns(request.getViewInfo());
                    }

                    request.getViewInfo().getChartExtRequest().setPageSize(Long.valueOf(extractPageSize));
                    ChartViewDTO chartViewDTO = findExcelData(request);
                    for (long i = 1; i < chartViewDTO.getTotalPage() + 1; i++) {
                        request.getViewInfo().getChartExtRequest().setGoPage(i);
                        ChartViewDTO pageDto = findExcelData(request);
                        details.addAll(request.getDetails());

                        if (summaryEnabled) {
                            accumulatePageStats(summaryAcc, request.getDetails(), allExportColumns, summaryConfig);
                            if (i == chartViewDTO.getTotalPage() && pageDto.getData() != null && pageDto.getData().get("customSumResult") != null) {
                                customSumResult = (Map<String, BigDecimal>) pageDto.getData().get("customSumResult");
                            }
                        }

                        if ((details.size() + extractPageSize) > sheetLimit || i == chartViewDTO.getTotalPage()) {
                            if (i == chartViewDTO.getTotalPage() && summaryEnabled && summaryAcc.totalCount > 0) {
                                Object[] totalRow = buildSummaryRow(allExportColumns, summaryConfig, summaryAcc, customSumResult);
                                details.add(totalRow);
                            }

                            detailsSheet = wb.createSheet("数据" + sheetIndex);
                            Integer[] excelTypes = request.getExcelTypes();
                            List<ChartViewFieldDTO> xAxis = new ArrayList<>();
                            xAxis.addAll(request.getViewInfo().getXAxis());
                            xAxis.addAll(request.getViewInfo().getYAxis());
                            xAxis.addAll(request.getViewInfo().getXAxisExt());
                            xAxis.addAll(request.getViewInfo().getYAxisExt());
                            xAxis.addAll(request.getViewInfo().getExtStack());
                            Object[] header = Arrays.stream(request.getHeader()).filter(item -> xAxis.stream().map(d -> StringUtils.isNotBlank(d.getChartShowName()) ? d.getChartShowName() : d.getName()).toList().contains(item)).collect(Collectors.toList()).toArray();
                            details.add(0, header);
                            List<Integer> columnIndexs = new ArrayList<>();
                            for (int i1 = 0; i1 < xAxis.size(); i1++) {
                                ChartViewFieldDTO xAxi = xAxis.get(i1);
                                if (xAxi.isHide()) {
                                    columnIndexs.add(i1);
                                }
                            }
                            ExportCenterDownLoadManage.removeColumn(details, columnIndexs);
                            ViewDetailField[] detailFields = request.getDetailFields();
                            ChartDataServer.setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), wb);
                            sheetIndex++;
                            details.clear();
                        }
                    }
                } else {
                    findExcelData(request);
                    if (CollectionUtils.isEmpty(request.getMultiInfo())) {
                        List<Object[]> details = request.getDetails();
                        Integer[] excelTypes = request.getExcelTypes();
                        details.add(0, request.getHeader());
                        ViewDetailField[] detailFields = request.getDetailFields();
                        Object[] header = request.getHeader();
                        Sheet detailsSheet = wb.createSheet("数据");
                        if (request.getViewInfo().getType().equalsIgnoreCase("table-normal")) {
                            setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), wb);
                        } else {
                            setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), null);
                        }
                    } else {
                        for (int i = 0; i < request.getMultiInfo().size(); i++) {
                            ChartExcelRequestInner requestInner = request.getMultiInfo().get(i);
                            List<Object[]> details = requestInner.getDetails();
                            Integer[] excelTypes = requestInner.getExcelTypes();
                            details.add(0, requestInner.getHeader());
                            ViewDetailField[] detailFields = requestInner.getDetailFields();
                            Object[] header = requestInner.getHeader();
                            Sheet detailsSheet = wb.createSheet("数据 " + (i + 1));
                            setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), null);
                        }
                    }
                }
                exportCenterManage.addWatermarkTools(wb);
                response.setContentType("application/vnd.ms-excel");
                // 通过响应头写入导出文件名
                response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(request.getViewName(), StandardCharsets.UTF_8) + ".xlsx");
                wb.write(outputStream);
                outputStream.flush();
                outputStream.close();

                try {
                    if (request.getBusiFlag().equalsIgnoreCase("dashboard")) {
                        CommonBeanFactory.proxy(this.getClass()).exportPanelViewLog(Long.parseLong(request.getViewId()));
                    } else {
                        CommonBeanFactory.proxy(this.getClass()).exportScreenViewLog(Long.parseLong(request.getViewId()));
                    }
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            } catch (Exception e) {
                CrestException.throwException(e);
            }
        } else {
            exportCenterManage.addTask(request.getViewId(), "chart", request, request.getBusiFlag());
        }
    }

    /**
     * 判断当前导出是否应直接写入 HTTP 响应
     */
    public static boolean shouldWriteDirectExport(String linkToken, boolean embeddedExport) {
        return StringUtils.isNotEmpty(linkToken) || embeddedExport;
    }

    /**
     * 导出表格类视图前统一修正分页模式，避免导出场景沿用前端滚动模式
     */
    public static void prepareTableViewForExport(ChartExcelRequest request) {
        if (request == null || request.getViewInfo() == null) {
            return;
        }
        ChartViewDTO viewInfo = request.getViewInfo();
        boolean tableExport = Strings.CI.equalsAny(viewInfo.getType(), "table-info", "table-normal")
                || "dataset".equals(request.getDownloadType());
        if (!tableExport || viewInfo.getCustomAttr() == null) {
            return;
        }
        Object basicStyle = viewInfo.getCustomAttr().get("basicStyle");
        if (basicStyle instanceof Map<?, ?> basicStyleMap) {
            ((Map<String, Object>) basicStyleMap).put("tablePageMode", "page");
        }
    }

    /**
     * 兼容数据集明细导出接口，复用图表明细导出流程
     */
    @CrestLinkPermit("#p0.dvId")
    @Override
    public void innerExportDataSetDetails(ChartExcelRequest request, HttpServletResponse response) throws Exception {
        this.innerExportDetails(request, response);
    }

    /**
     * 校验导出请求并补齐图表扩展请求的默认筛选容器
     */
    private void ensureExportRequest(ChartExcelRequest request) {
        if (request == null || request.getViewInfo() == null) {
            CrestException.throwException("导出图表不存在或已经被删除");
        }
        if (request.getViewInfo().getChartExtRequest() == null) {
            ChartExtRequest chartExtRequest = new ChartExtRequest();
            chartExtRequest.setFilter(new ArrayList<>());
            chartExtRequest.setLinkageFilters(new ArrayList<>());
            chartExtRequest.setOuterParamsFilters(new ArrayList<>());
            chartExtRequest.setWebParamsFilters(new ArrayList<>());
            chartExtRequest.setDrill(new ArrayList<>());
            request.getViewInfo().setChartExtRequest(chartExtRequest);
        }
        prepareTableViewForExport(request);
    }

    /**
     * 使用默认批注配置写入 Excel 明细数据
     */
    public static void setExcelData(Sheet detailsSheet, CellStyle cellStyle, Object[] header, List<Object[]> details, ViewDetailField[] detailFields, Integer[] excelTypes, ChartViewDTO viewInfo, Workbook wb) {
        setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, null, viewInfo, wb);
    }


    /**
     * 将导出明细、复杂表头、字段格式和合并单元格配置写入工作表
     */
    public static void setExcelData(Sheet detailsSheet, CellStyle cellStyle, Object[] header, List<Object[]> details, ViewDetailField[] detailFields, Integer[] excelTypes, Comment comment, ChartViewDTO viewInfo, Workbook wb) {
        List<CellStyle> styles = new ArrayList<>();
        List<ChartViewFieldDTO> xAxis = new ArrayList<>();

        xAxis.addAll(viewInfo.getXAxis());
        xAxis.addAll(viewInfo.getYAxis());
        xAxis.addAll(viewInfo.getXAxisExt());
        xAxis.addAll(viewInfo.getYAxisExt());
        xAxis.addAll(viewInfo.getExtStack());
        xAxis.addAll(viewInfo.getDrillFields());
        TableHeader tableHeader = null;
        Integer totalDepth = 0;
        List<CellRangeAddress> mergeConfig = new ArrayList<>();
        if (Strings.CI.equalsAny(viewInfo.getType(), "table-normal", "table-info")) {
            for (ChartViewFieldDTO tmpAxis : xAxis) {
                if (tmpAxis.isHide()) {
                    continue;
                }
                if (tmpAxis.getFieldType().equals(FieldTypeConstants.INTEGER) || tmpAxis.getFieldType().equals(FieldTypeConstants.FLOAT)) {
                    CellStyle formatterCellStyle = createCellStyle(wb, tmpAxis.getFormatterCfg(), null);
                    styles.add(formatterCellStyle);
                } else {
                    styles.add(null);
                }
            }

            Map<String, Object> customAttr = viewInfo.getCustomAttr();
            Map<String, Object> tableHeaderMap = customAttr == null ? null : (Map<String, Object>) customAttr.get("tableHeader");
            TableHeader tmpHeader = null;
            var allAxis = new ArrayList<>(viewInfo.getXAxis().stream().filter(x -> !x.isHide()).toList());
            if (Strings.CI.equals(viewInfo.getType(), "table-normal")) {
                allAxis.addAll(viewInfo.getYAxis().stream().filter(x -> !x.isHide()).toList());
            }
            if (tableHeaderMap != null) {
                tmpHeader = JsonUtil.parseObject((String) JsonUtil.toJSONString(tableHeaderMap), TableHeader.class);
            }
            if (tmpHeader != null && tmpHeader.isHeaderGroup() && validateHeaderGroup(tmpHeader, allAxis)) {
                tableHeader = tmpHeader;
            }
            if (tableHeader == null && isAuxiliaryHeaderEnabled(tmpHeader)) {
                tableHeader = buildAuxiliaryHeader(tmpHeader, allAxis);
            } else if (tableHeader != null && isAuxiliaryHeaderEnabled(tableHeader)) {
                applyAuxiliaryHeader(tableHeader, allAxis);
            }
            if (tableHeader != null) {
                for (TableHeader.ColumnInfo column : tableHeader.getHeaderGroupConfig().getColumns()) {
                    totalDepth = Math.max(totalDepth, getDepth(column, 1));
                }
                for (TableHeader.ColumnInfo column : tableHeader.getHeaderGroupConfig().getColumns()) {
                    setWidth(column, 1);
                }
            }
            if ("table-info".equalsIgnoreCase(viewInfo.getType()) && !"dataset".equalsIgnoreCase(viewInfo.getDownloadType())) {
                xAxis = xAxis.stream().filter(x -> !x.isHide()).toList();
                Map<String, Object> tableCell = (Map<String, Object>) viewInfo.getCustomAttr().get("tableCell");
                Boolean mergeCells = (Boolean) tableCell.get("mergeCells");
                if (mergeCells != null && mergeCells) {
                    var tmpAxis = viewInfo.getXAxis().stream().filter(x -> !x.isHide()).toList();
                    var mergeIndex = tmpAxis.size();
                    for (int i = 0; i < tmpAxis.size(); i++) {
                        if ("q".equalsIgnoreCase(tmpAxis.get(i).getGroupType())) {
                            mergeIndex = i;
                            break;
                        }
                    }
                    if (mergeIndex >= 1 && details.size() > 1) {
                        mergeConfig = getMergeConfig(details.subList(1, details.size()), mergeIndex - 1, totalDepth == 0 ? 1 : totalDepth);
                    }
                }
            }
        }

        boolean mergeHead = false;
        if (ArrayUtils.isNotEmpty(detailFields)) {
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            String[] detailField = Arrays.stream(detailFields).map(ViewDetailField::getName).toList().toArray(new String[detailFields.length]);

            Row row = detailsSheet.createRow(0);
            int headLen = header.length;
            int detailFieldLen = detailField.length;
            for (int i = 0; i < headLen; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(header[i].toString());
                if (i < headLen - 1) {
                    CellRangeAddress cellRangeAddress = new CellRangeAddress(0, 1, i, i);
                    detailsSheet.addMergedRegion(cellRangeAddress);
                } else {
                    for (int j = i + 1; j < detailFieldLen + i; j++) {
                        row.createCell(j).setCellStyle(cellStyle);
                    }
                    CellRangeAddress cellRangeAddress = new CellRangeAddress(0, 0, i, i + detailFieldLen - 1);
                    detailsSheet.addMergedRegion(cellRangeAddress);
                }
                cell.setCellStyle(cellStyle);
                detailsSheet.setColumnWidth(i, 255 * 20);
            }

            Row detailRow = detailsSheet.createRow(1);
            for (int i = 0; i < headLen - 1; i++) {
                Cell cell = detailRow.createCell(i);
                cell.setCellStyle(cellStyle);
            }
            for (int i = 0; i < detailFieldLen; i++) {
                int colIndex = headLen - 1 + i;
                Cell cell = detailRow.createCell(colIndex);
                cell.setCellValue(detailField[i]);
                cell.setCellStyle(cellStyle);
                detailsSheet.setColumnWidth(colIndex, 255 * 20);
            }
            details.add(1, detailField);
            mergeHead = true;
        }
        if (CollectionUtils.isNotEmpty(details) && (!mergeHead || details.size() > 2)) {
            int realDetailRowIndex = tableHeader == null ? 2 : totalDepth;
            if (tableHeader != null) {
                cellStyle.setBorderTop(BorderStyle.THIN);
                cellStyle.setBorderRight(BorderStyle.THIN);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBorderLeft(BorderStyle.THIN);
                CellStyle auxiliaryHeaderCellStyle = createAuxiliaryHeaderCellStyle(wb, tableHeader);
                Map<String, Row> rowMap = new HashMap<>();
                for (Integer i = 0; i < totalDepth; i++) {
                    Row headerRow = detailsSheet.createRow(i);
                    setHeaderRowHeight(headerRow, tableHeader, i, totalDepth);
                    rowMap.put("row" + i, headerRow);
                }
                int width = 0;
                Integer depth = 0;
                for (TableHeader.ColumnInfo column : tableHeader.getHeaderGroupConfig().getColumns()) {
                    createCell(tableHeader, column, width, depth, detailsSheet, cellStyle, auxiliaryHeaderCellStyle, totalDepth, rowMap, xAxis);
                    width = width + column.getWidth();
                }
            }
            for (int i = (mergeHead ? 2 : 0); i < details.size(); i++) {
                if (tableHeader != null && i == 0) {
                    continue;
                }
                int rowIndex = i;
                if (tableHeader != null) {
                    rowIndex = realDetailRowIndex + i - 1;
                } else {
                    rowIndex = realDetailRowIndex > 2 ? realDetailRowIndex : i;
                }
                Row row = detailsSheet.createRow(rowIndex);
                Object[] rowData = details.get(i);
                if (rowData != null) {
                    for (int j = 0; j < rowData.length; j++) {
                        Object cellValObj = rowData[j];
                        if (mergeHead && j == rowData.length - 1 && (cellValObj.getClass().isArray() || cellValObj instanceof ArrayList)) {
                            Object[] detailRowArray = ((List<Object>) cellValObj).toArray(new Object[((List<?>) cellValObj).size()]);
                            int detailRowArrayLen = detailRowArray.length;
                            int temlJ = j;
                            while (detailRowArrayLen > 1 && temlJ-- > 0) {
                                CellRangeAddress cellRangeAddress = new CellRangeAddress(realDetailRowIndex, realDetailRowIndex + detailRowArrayLen - 1, temlJ, temlJ);
                                detailsSheet.addMergedRegion(cellRangeAddress);
                            }

                            for (int k = 0; k < detailRowArrayLen; k++) {
                                List<Object> detailRows = (List<Object>) detailRowArray[k];
                                Row curRow = row;
                                if (k > 0) {
                                    curRow = detailsSheet.createRow(realDetailRowIndex + k);
                                }

                                for (int l = 0; l < detailRows.size(); l++) {
                                    Object col = detailRows.get(l);
                                    Cell cell = curRow.createCell(j + l);
                                    cell.setCellValue(col.toString());
                                }
                            }
                            realDetailRowIndex += detailRowArrayLen;
                            break;
                        }

                        Cell cell = row.createCell(j);
                        if (i == 0) {
                            // 首行作为普通表头写入并应用表头样式
                            cell.setCellValue(cellValObj.toString());
                            cell.setCellStyle(cellStyle);
                            // 普通导出列使用固定宽度
                            detailsSheet.setColumnWidth(j, 255 * 20);
                        } else if (cellValObj != null) {
                            try {
                                if (Strings.CI.equalsAny(viewInfo.getType(), "table-info", "table-normal") && Arrays.asList(FieldTypeConstants.INTEGER,FieldTypeConstants.FLOAT).contains(xAxis.get(j).getFieldType())) {
                                    try {
                                        FormatterCfgDTO formatterCfgDTO = xAxis.get(j).getFormatterCfg() == null ? new FormatterCfgDTO().setUnitLanguage(Lang.isChinese() ? "ch" : "en") : xAxis.get(j).getFormatterCfg();
                                        row.getCell(j).setCellStyle(styles.get(j));
                                        row.getCell(j).setCellValue(Double.valueOf(cellValue(formatterCfgDTO, new BigDecimal(cellValObj.toString()))));
                                    } catch (Exception e) {
                                        cell.setCellValue(cellValObj.toString());
                                    }
                                } else {
                                    if ((excelTypes[j].equals(FieldTypeConstants.INTEGER) || excelTypes[j].equals(FieldTypeConstants.FLOAT)) && StringUtils.isNotEmpty(cellValObj.toString())) {
                                        cell.setCellValue(Double.valueOf(cellValObj.toString()));
                                    } else if (cellValObj != null) {
                                        cell.setCellValue(cellValObj.toString());
                                    }
                                }
                            } catch (Exception e) {
                                LogUtil.warn("export excel data transform error");
                            }
                        } else {
                            if (!viewInfo.getType().equalsIgnoreCase("circle-packing")) {
                                Map<String, Object> senior = viewInfo.getSenior();
                                viewInfo.getCustomAttr().get("");
                                ChartSeniorFunctionCfgDTO functionCfgDTO = JsonUtil.parseObject((String) JsonUtil.toJSONString(senior.get("functionCfg")), ChartSeniorFunctionCfgDTO.class);
                                if (functionCfgDTO != null && StringUtils.isNotEmpty(functionCfgDTO.getEmptyDataStrategy()) && functionCfgDTO.getEmptyDataStrategy().equalsIgnoreCase("setZero")) {
                                    if ((viewInfo.getType().equalsIgnoreCase("table-normal") || viewInfo.getType().equalsIgnoreCase("table-info"))) {
                                        if (functionCfgDTO.getEmptyDataFieldCtrl().contains(xAxis.get(j).getEngineFieldName())) {
                                            cell.setCellValue(0);
                                        }
                                    } else {
                                        cell.setCellValue(0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(mergeConfig)) {
                mergeConfig.forEach(detailsSheet::addMergedRegionUnsafe);
            }
        }
    }

    /**
     * 根据相邻行的同列值计算表格明细导出时的纵向合并区域
     */
    private static List<CellRangeAddress> getMergeConfig(List<Object[]> data, int colIndex, int offsetHeight) {
        var result = new ArrayList<CellRangeAddress>();
        var preRange = new ArrayList<Integer[]>();
        var initRange = new Integer[]{0, data.size() - 1};
        preRange.add(initRange);
        for (int curColIndex = 0; curColIndex <= colIndex; curColIndex++) {
            var curRange = new ArrayList<Integer[]>();
            for (int preRangeIndex = 0; preRangeIndex < preRange.size(); preRangeIndex++) {
                var preRowRange = preRange.get(preRangeIndex);
                var start = preRowRange[0];
                var end = preRowRange[1];
                var lastColValue = data.get(start)[curColIndex];
                if (lastColValue != null) {
                    lastColValue = lastColValue.toString();
                } else {
                    lastColValue = "";
                }
                var lastRowIndex = start;
                for (Integer curRowIndex = start + 1; curRowIndex <= end; curRowIndex++) {
                    var curRow = data.get(curRowIndex);
                    var curColValue = curRow[curColIndex];
                    if (curColValue != null) {
                        curColValue = curColValue.toString();
                    } else {
                        curColValue = "";
                    }
                    if (!Strings.CS.equals(lastColValue.toString(), curColValue.toString()) && (curRowIndex - lastRowIndex > 1)) {
                        curRange.add(new Integer[]{lastRowIndex, curRowIndex - 1});
                        result.add(new CellRangeAddress(lastRowIndex + offsetHeight, curRowIndex + offsetHeight - 1, curColIndex, curColIndex));
                    }
                    if (curRowIndex.equals(end) && curColValue.equals(lastColValue) && curRowIndex - lastRowIndex > 0) {
                        curRange.add(new Integer[]{lastRowIndex, curRowIndex});
                        result.add(new CellRangeAddress(lastRowIndex + offsetHeight, curRowIndex + offsetHeight, curColIndex, curColIndex));
                    }
                    if (!Strings.CS.equals(lastColValue.toString(), curColValue.toString())) {
                        lastColValue = curColValue;
                        lastRowIndex = curRowIndex;
                    }
                }
            }
            preRange = curRange;
        }
        return result;
    }

    /**
     * 校验复杂表头叶子列是否与导出字段顺序一致
     */
    private static boolean validateHeaderGroup(TableHeader header, List<ChartViewFieldDTO> fields) {
        if (header == null) {
            return false;
        }
        if (header.getHeaderGroupConfig() == null) {
            return false;
        }
        var columns = header.getHeaderGroupConfig().getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            return false;
        }
        var leafColumn = getHeaderLeafColumn(columns);
        if (CollectionUtils.isEmpty(leafColumn) || leafColumn.size() != fields.size()) {
            return false;
        }
        for (int i = 0; i < leafColumn.size(); i++) {
            var a = leafColumn.get(i);
            var b = fields.get(i).getEngineFieldName();
            if (!Strings.CS.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按表头树顺序收集所有叶子字段标识
     */
    private static List<String> getHeaderLeafColumn(List<TableHeader.ColumnInfo> columns) {
        var result = new ArrayList<String>();
        for (TableHeader.ColumnInfo column : columns) {
            if (CollectionUtils.isEmpty(column.getChildren())) {
                result.add(column.getKey());
            } else {
                result.addAll(getHeaderLeafColumn(column.getChildren()));
            }
        }
        return result;
    }

    /**
     * 判断是否启用辅助表头说明
     */
    private static boolean isAuxiliaryHeaderEnabled(TableHeader tableHeader) {
        return tableHeader != null && tableHeader.getAuxiliaryHeader() != null && tableHeader.getAuxiliaryHeader().isEnabled();
    }

    /**
     * 在没有复杂表头时为字段列表构造辅助表头分组
     */
    private static TableHeader buildAuxiliaryHeader(TableHeader sourceHeader, List<ChartViewFieldDTO> fields) {
        TableHeader tableHeader = sourceHeader == null ? new TableHeader() : sourceHeader;
        TableHeader.HeaderGroupConfig headerGroupConfig = new TableHeader.HeaderGroupConfig();
        for (ChartViewFieldDTO field : fields) {
            TableHeader.ColumnInfo group = new TableHeader.ColumnInfo();
            group.setKey(AUXILIARY_HEADER_GROUP_PREFIX + field.getEngineFieldName());
            TableHeader.ColumnInfo leaf = new TableHeader.ColumnInfo();
            leaf.setKey(field.getEngineFieldName());
            group.setChildren(new ArrayList<>(List.of(leaf)));
            headerGroupConfig.getColumns().add(group);
        }
        tableHeader.setHeaderGroup(true);
        tableHeader.setHeaderGroupConfig(headerGroupConfig);
        return tableHeader;
    }

    /**
     * 在已有复杂表头上补齐辅助说明需要的根分组
     */
    private static void applyAuxiliaryHeader(TableHeader tableHeader, List<ChartViewFieldDTO> fields) {
        if (tableHeader == null || tableHeader.getHeaderGroupConfig() == null || CollectionUtils.isEmpty(tableHeader.getHeaderGroupConfig().getColumns())) {
            return;
        }
        var fieldNames = fields.stream().map(ChartViewFieldDTO::getEngineFieldName).collect(Collectors.toSet());
        var columns = tableHeader.getHeaderGroupConfig().getColumns().stream()
                .map(column -> applyAuxiliaryColumn(column, true, fieldNames))
                .toList();
        tableHeader.getHeaderGroupConfig().setColumns(new ArrayList<>(columns));
    }

    /**
     * 递归处理单列表头节点，必要时把根字段包装为辅助分组
     */
    private static TableHeader.ColumnInfo applyAuxiliaryColumn(TableHeader.ColumnInfo column, boolean root, Set<String> fieldNames) {
        if (column == null || StringUtils.isBlank(column.getKey())) {
            return column;
        }
        if (CollectionUtils.isEmpty(column.getChildren())) {
            if (!root || !fieldNames.contains(column.getKey())) {
                return column;
            }
            TableHeader.ColumnInfo group = new TableHeader.ColumnInfo();
            group.setKey(AUXILIARY_HEADER_GROUP_PREFIX + column.getKey());
            group.setChildren(new ArrayList<>(List.of(column)));
            return group;
        }
        var children = column.getChildren().stream()
                .map(child -> applyAuxiliaryColumn(child, false, fieldNames))
                .toList();
        column.setChildren(new ArrayList<>(children));
        return column;
    }

    /**
     * 获取叶子表头显示名称，辅助表头优先使用说明文本
     */
    private static String getLeafHeaderName(TableHeader tableHeader, List<ChartViewFieldDTO> xAxis, String key) {
        if (isAuxiliaryHeaderEnabled(tableHeader)) {
            return getAuxiliaryDescription(tableHeader, key);
        }
        return getDeFieldName(xAxis, key);
    }

    /**
     * 查询辅助表头中指定字段的说明文本
     */
    private static String getAuxiliaryDescription(TableHeader tableHeader, String key) {
        if (tableHeader == null || tableHeader.getAuxiliaryHeader() == null || CollectionUtils.isEmpty(tableHeader.getAuxiliaryHeader().getDescriptions())) {
            return "";
        }
        for (TableHeader.DescriptionInfo descriptionInfo : tableHeader.getAuxiliaryHeader().getDescriptions()) {
            if (descriptionInfo != null && Strings.CS.equals(descriptionInfo.getField(), key)) {
                return StringUtils.defaultString(descriptionInfo.getText());
            }
        }
        return "";
    }

    /**
     * 创建辅助表头单元格样式
     */
    private static CellStyle createAuxiliaryHeaderCellStyle(Workbook workbook, TableHeader tableHeader) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setWrapText(true);
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellStyle.setAlignment(resolveAuxiliaryHeaderAlignment(tableHeader));
        cellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setBold(false);
        Integer fontSize = isAuxiliaryHeaderEnabled(tableHeader) ? tableHeader.getAuxiliaryHeader().getFontSize() : null;
        font.setFontHeightInPoints((short) Math.max(8, Optional.ofNullable(fontSize).orElse(14)));
        font.setColor(IndexedColors.BLACK.getIndex());
        cellStyle.setFont(font);
        return cellStyle;
    }

    /**
     * 将辅助表头对齐配置转换为 Excel 单元格对齐方式
     */
    private static HorizontalAlignment resolveAuxiliaryHeaderAlignment(TableHeader tableHeader) {
        if (tableHeader == null || tableHeader.getAuxiliaryHeader() == null || StringUtils.isBlank(tableHeader.getAuxiliaryHeader().getAlign())) {
            return HorizontalAlignment.CENTER;
        }
        return switch (tableHeader.getAuxiliaryHeader().getAlign()) {
            case "left" -> HorizontalAlignment.LEFT;
            case "right" -> HorizontalAlignment.RIGHT;
            default -> HorizontalAlignment.CENTER;
        };
    }

    /**
     * 按辅助表头配置设置末级表头行高度
     */
    private static void setHeaderRowHeight(Row row, TableHeader tableHeader, Integer rowIndex, Integer totalDepth) {
        if (!isAuxiliaryHeaderEnabled(tableHeader) || !Objects.equals(rowIndex, totalDepth - 1)) {
            return;
        }
        Integer rowHeight = tableHeader.getAuxiliaryHeader().getRowHeight();
        if (rowHeight != null && rowHeight > 0) {
            row.setHeightInPoints((float) (rowHeight * 0.75));
        }
    }

    /**
     * 计算表头树的最大深度
     */
    private static Integer getDepth(TableHeader.ColumnInfo column, Integer parentDepth) {
        if (org.springframework.util.CollectionUtils.isEmpty(column.getChildren())) {
            return parentDepth;
        } else {
            Integer depth = 0;
            for (TableHeader.ColumnInfo child : column.getChildren()) {
                depth = Math.max(depth, getDepth(child, parentDepth + 1));
            }
            return depth;
        }
    }

    /**
     * 递归创建复杂表头单元格和合并区域
     */
    private static void createCell(TableHeader tableHeader, TableHeader.ColumnInfo column, Integer width, Integer depth, Sheet sheet, CellStyle cellStyle, CellStyle auxiliaryHeaderCellStyle, Integer totalDepth, Map<String, Row> rowMap, List<ChartViewFieldDTO> xAxis) {
        if (org.springframework.util.CollectionUtils.isEmpty(column.getChildren())) {
            Integer toDepth = totalDepth - 1 > depth ? totalDepth - 1 : depth;
            CellStyle headerCellStyle = isAuxiliaryHeaderEnabled(tableHeader) ? auxiliaryHeaderCellStyle : cellStyle;
            String leafName = getLeafHeaderName(tableHeader, xAxis, column.getKey());
            if (depth.equals(toDepth)) {
                Cell cell = rowMap.get("row" + depth).createCell(width);
                cell.setCellStyle(headerCellStyle);
                cell.setCellValue(leafName);
            } else {
                for (int i = depth; i <= toDepth; i++) {
                    Cell cell1 = rowMap.get("row" + i).createCell(width);
                    cell1.setCellValue(leafName);
                    cell1.setCellStyle(headerCellStyle);
                }
                CellRangeAddress region = new CellRangeAddress(depth, toDepth, width, width);
                sheet.addMergedRegion(region);
                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                Cell mergedCell = rowMap.get("row" + depth).getCell(width);
                mergedCell.setCellStyle(headerCellStyle);

            }
        } else {
            Cell cell1 = rowMap.get("row" + depth).createCell(width);
            cell1.setCellValue(getGroupName(tableHeader, column.getKey(), xAxis));
            cell1.setCellStyle(cellStyle);
            if (column.getWidth() != null && column.getWidth() > 1) {
                Cell cell2 = rowMap.get("row" + depth).createCell(width + column.getWidth() - 1);
                cell2.setCellValue(getGroupName(tableHeader, column.getKey(), xAxis));
                cell2.setCellStyle(cellStyle);
                CellRangeAddress region = new CellRangeAddress(depth, depth, width, width + column.getWidth() - 1);
                sheet.addMergedRegion(region);
                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
                Cell mergedCell = rowMap.get("row" + depth).getCell(width);
                mergedCell.setCellStyle(cellStyle);
            }
            int subWith = width;
            for (TableHeader.ColumnInfo child : column.getChildren()) {
                createCell(tableHeader, child, subWith, depth + 1, sheet, cellStyle, auxiliaryHeaderCellStyle, totalDepth, rowMap, xAxis);
                subWith = subWith + child.getWidth();
            }
        }
    }

    /**
     * 获取复杂表头分组名称
     */
    private static String getGroupName(TableHeader tableHeader, String key, List<ChartViewFieldDTO> xAxis) {
        if (key != null && key.startsWith(AUXILIARY_HEADER_GROUP_PREFIX)) {
            return getDeFieldName(xAxis, key.substring(AUXILIARY_HEADER_GROUP_PREFIX.length()));
        }
        for (TableHeader.MetaInfo metaInfo : tableHeader.getHeaderGroupConfig().getMeta()) {
            if (metaInfo.getField().equals(key)) {
                return metaInfo.getName();
            }
        }
        return "";
    }

    /**
     * 根据字段标识获取导出展示名称
     */
    private static String getDeFieldName(List<ChartViewFieldDTO> xAxis, String key) {
        for (ChartViewFieldDTO xAxi : xAxis) {
            if (xAxi.getEngineFieldName().equals(key)) {
                return StringUtils.isNotBlank(xAxi.getChartShowName()) ? xAxi.getChartShowName() : xAxi.getName();
            }
        }
        return "";
    }

    /**
     * 递归计算复杂表头每个节点占用的叶子列宽度
     */
    private static Integer setWidth(TableHeader.ColumnInfo column, Integer parentWidth) {
        if (org.springframework.util.CollectionUtils.isEmpty(column.getChildren())) {
            column.setWidth(parentWidth);
            return parentWidth;
        } else {
            Integer depth = 0;
            for (TableHeader.ColumnInfo child : column.getChildren()) {
                depth = depth + setWidth(child, 1);
            }
            column.setWidth(depth);
            return depth;
        }
    }

    /**
     * 将导出数值按单位转换为 Excel 可用的原始值
     */
    private static String cellValue(FormatterCfgDTO formatterCfgDTO, BigDecimal value) {
        if (formatterCfgDTO.getType().equalsIgnoreCase("percent")) {
            return value.toString();
        } else {
            return value.divide(BigDecimal.valueOf(formatterCfgDTO.getUnit())).toString();
        }
    }

    /**
     * 根据图表字段格式化配置创建 Excel 数值格式样式
     */
    private static CellStyle createCellStyle(Workbook workbook, FormatterCfgDTO formatter, String value) {
        CellStyle cellStyle = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();

        if (formatter == null) {
            cellStyle.setDataFormat(format.getFormat("General"));
            return cellStyle;
        }
        String formatStr = "";
        if (formatter.getType().equals("auto")) {
            String[] valueSplit = String.valueOf(value).split(".");
            if (StringUtils.isEmpty(value) || !value.contains(".")) {
                formatStr = "General";
            } else {
                formatStr = "0." + new String(new char[valueSplit.length]).replace('\0', '0');
            }
            switch (formatter.getUnit()) {
                case 1000:
                    formatStr = formatStr + (formatter.getUnitLanguage().equalsIgnoreCase("ch") ? "\"千\"" : "\"K\"");
                    break;
                case 10000:
                    formatStr = formatStr + "\"万\"";
                    break;
                case 1000000:
                    formatStr = formatStr + (formatter.getUnitLanguage().equalsIgnoreCase("ch") ? "\"百万\"" : "\"M\"");
                    break;
                case 100000000:
                    formatStr = formatStr + "\"亿\"";
                    break;
                case 1000000000:
                    formatStr = formatStr + "\"B\"";
                    break;
                default:
                    break;
            }
            if (formatter.getThousandSeparator()) {
                formatStr = "#,##" + formatStr;
            }
            if (StringUtils.isNotEmpty(formatter.getSuffix())) {
                if (formatter.getSuffix().equals("%")) {
                    formatStr = formatStr + "\"%\"";
                } else {
                    formatStr = formatStr + "\"" + formatter.getSuffix() + "\"";
                }
            }
        }
        if (formatter.getType().equals("value")) {
            if (formatter.getDecimalCount() > 0) {
                formatStr = "0." + new String(new char[formatter.getDecimalCount()]).replace('\0', '0');
            } else {
                formatStr = "0";
            }
            switch (formatter.getUnit()) {
                case 1000:
                    formatStr = formatStr + (formatter.getUnitLanguage().equalsIgnoreCase("ch") ? "\"千\"" : "\"K\"");
                    break;
                case 10000:
                    formatStr = formatStr + "\"万\"";
                    break;
                case 1000000:
                    formatStr = formatStr + (formatter.getUnitLanguage().equalsIgnoreCase("ch") ? "\"百万\"" : "\"M\"");
                    break;
                case 100000000:
                    formatStr = formatStr + "\"亿\"";
                    break;
                case 1000000000:
                    formatStr = formatStr + "\"B\"";
                    break;
                default:
                    break;
            }
            if (formatter.getThousandSeparator()) {
                formatStr = "#,##" + formatStr;
            }
            if (StringUtils.isNotEmpty(formatter.getSuffix())) {
                if (formatter.getSuffix().equals("%")) {
                    formatStr = formatStr + "\"%\"";
                } else {
                    formatStr = formatStr + "\"" + formatter.getSuffix() + "\"";
                }
            }
        } else if (formatter.getType().equals("percent")) {
            if (formatter.getDecimalCount() > 0) {
                formatStr = "0." + new String(new char[formatter.getDecimalCount()]).replace('\0', '0');
            } else {
                formatStr = "0";
            }
            formatStr = formatStr + "%";
        }
        if (StringUtils.isNotEmpty(formatStr)) {
            cellStyle.setDataFormat(format.getFormat(formatStr));
        } else {
            return null;
        }
        return cellStyle;
    }

    /**
     * 查询指定字段的可选值
     */
    @Override
    public List<String> getFieldData(ChartViewDTO view, Long fieldId, String fieldType) throws Exception {
        return chartDataManage.getFieldData(view, fieldId, fieldType);
    }

    /**
     * 查询钻取字段的可选值
     */
    @Override
    public List<String> getDrillFieldData(ChartViewDTO view, Long fieldId) throws Exception {
        return chartDataManage.getDrillFieldData(view, fieldId);
    }

    /**
     * 记录仪表板视图导出审计日志
     */
    @CrestAudit(id = "#p0", ot = LogOT.EXPORT, st = LogST.PANEL)
    public void exportPanelViewLog(Long id) {
    }

    /**
     * 记录数据大屏视图导出审计日志
     */
    @CrestAudit(id = "#p0", ot = LogOT.EXPORT, st = LogST.SCREEN)
    public void exportScreenViewLog(Long id) {
    }

    /**
     * 判断表格类图表是否开启汇总行导出
     */
    public static boolean isSummaryEnabled(ChartViewDTO viewInfo) {
        if (viewInfo == null || viewInfo.getCustomAttr() == null) return false;
        String type = viewInfo.getType();
        if (!Strings.CI.equalsAny(type, "table-info", "table-normal")) return false;
        Map<String, Object> basicStyle = (Map<String, Object>) viewInfo.getCustomAttr().get("basicStyle");
        if (basicStyle == null) return false;
        return basicStyle.get("showSummary") != null && (Boolean) basicStyle.get("showSummary");
    }

    /**
     * 解析汇总行配置，生成字段级汇总类型和显示开关
     */
    public static SummaryConfig parseSummaryConfig(ChartViewDTO viewInfo) {
        SummaryConfig config = new SummaryConfig();
        Map<String, Object> basicStyle = (Map<String, Object>) viewInfo.getCustomAttr().get("basicStyle");
        config.summaryLabel = (basicStyle.get("summaryLabel") != null && StringUtils.isNotBlank(basicStyle.get("summaryLabel").toString()))
                ? basicStyle.get("summaryLabel").toString()
                : (Lang.isChinese() ? "总计" : "Total");

        List<Map<String, Object>> seriesSummary = basicStyle.get("seriesSummary") != null
                ? (List<Map<String, Object>>) basicStyle.get("seriesSummary") : null;

        List<ChartViewFieldDTO> summaryFields;
        if (viewInfo.getType().equalsIgnoreCase("table-info")) {
            summaryFields = viewInfo.getXAxis();
        } else {
            summaryFields = new ArrayList<>();
            summaryFields.addAll(viewInfo.getXAxis());
            summaryFields.addAll(viewInfo.getYAxis());
        }

        for (ChartViewFieldDTO field : summaryFields) {
            String fName = field.getEngineFieldName();
            String sType = "sum";
            boolean sShow = true;
            if (seriesSummary != null) {
                for (Map<String, Object> s : seriesSummary) {
                    if (fName.equals(s.get("field"))) {
                        sType = s.get("summary") == null ? "sum" : s.get("summary").toString();
                        sShow = s.get("show") == null || (Boolean) s.get("show");
                        break;
                    }
                }
            }
            config.summaryTypeMap.put(fName, sType);
            config.summaryShowMap.put(fName, sShow);
        }
        return config;
    }

    /**
     * 累加单页导出明细的汇总统计数据
     */
    public static void accumulatePageStats(SummaryAccumulator acc, List<Object[]> pageDetails,
                                           List<ChartViewFieldDTO> allColumns, SummaryConfig config) {
        if (pageDetails == null) return;
        for (Object[] row : pageDetails) {
            acc.totalCount++;
            for (int j = 0; j < allColumns.size() && j < row.length; j++) {
                ChartViewFieldDTO field = allColumns.get(j);
                String fName = field.getEngineFieldName();
                if (!config.summaryShowMap.containsKey(fName) || !config.summaryShowMap.get(fName)) continue;
                String sType = config.summaryTypeMap.get(fName);
                if (sType == null || "custom".equals(sType)) continue;
                Object valObj = row[j];
                if (valObj == null || StringUtils.isBlank(valObj.toString())) continue;
                try {
                    BigDecimal val = new BigDecimal(valObj.toString());
                    switch (sType) {
                        case "max":
                            BigDecimal curMax = acc.maxMap.get(fName);
                            if (curMax == null || val.compareTo(curMax) > 0) acc.maxMap.put(fName, val);
                            break;
                        case "min":
                            BigDecimal curMin = acc.minMap.get(fName);
                            if (curMin == null || val.compareTo(curMin) < 0) acc.minMap.put(fName, val);
                            break;
                        default:
                            acc.sumMap.merge(fName, val, BigDecimal::add);
                            acc.countMap.merge(fName, 1L, Long::sum);
                            if ("var_pop".equals(sType) || "stddev_pop".equals(sType)) {
                                acc.sumOfSquaresMap.merge(fName, val.multiply(val), BigDecimal::add);
                            }
                            break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 根据汇总配置和统计结果构造导出汇总行
     */
    @SuppressWarnings("unchecked")
    public static Object[] buildSummaryRow(List<ChartViewFieldDTO> allColumns, SummaryConfig config,
                                           SummaryAccumulator acc, Map<String, BigDecimal> customSumResult) {
        Object[] totalRow = new Object[allColumns.size()];
        boolean labelSet = false;
        for (int j = 0; j < allColumns.size(); j++) {
            ChartViewFieldDTO field = allColumns.get(j);
            String fName = field.getEngineFieldName();
            if (config.summaryShowMap.containsKey(fName) && config.summaryShowMap.get(fName)) {
                String sType = config.summaryTypeMap.get(fName);
                switch (sType) {
                    case "custom":
                        totalRow[j] = customSumResult != null && customSumResult.get(fName) != null
                                ? customSumResult.get(fName).toPlainString() : null;
                        break;
                    case "max":
                        totalRow[j] = acc.maxMap.get(fName) != null ? acc.maxMap.get(fName).toPlainString() : null;
                        break;
                    case "min":
                        totalRow[j] = acc.minMap.get(fName) != null ? acc.minMap.get(fName).toPlainString() : null;
                        break;
                    case "avg":
                        BigDecimal sum = acc.sumMap.get(fName);
                        Long cnt = acc.countMap.get(fName);
                        if (sum != null && cnt != null && cnt > 0) {
                            totalRow[j] = sum.divide(BigDecimal.valueOf(cnt), 8, java.math.RoundingMode.HALF_UP).toPlainString();
                        }
                        break;
                    case "sum":
                        totalRow[j] = acc.sumMap.get(fName) != null ? acc.sumMap.get(fName).toPlainString() : null;
                        break;
                    case "var_pop":
                        totalRow[j] = calcVariance(acc, fName, false);
                        break;
                    case "stddev_pop":
                        totalRow[j] = calcVariance(acc, fName, true);
                        break;
                    default:
                        break;
                }
            } else if (!labelSet) {
                totalRow[j] = config.summaryLabel;
                labelSet = true;
            }
        }
        if (!labelSet && totalRow.length > 0) {
            totalRow[0] = config.summaryLabel;
        }
        return totalRow;
    }

    /**
     * 计算样本方差或样本标准差
     */
    private static String calcVariance(SummaryAccumulator acc, String fName, boolean isSqrt) {
        Long cnt = acc.countMap.get(fName);
        BigDecimal sum = acc.sumMap.get(fName);
        BigDecimal sumSq = acc.sumOfSquaresMap.get(fName);
        if (cnt == null || cnt < 2 || sum == null || sumSq == null) return null;
        BigDecimal mean = sum.divide(BigDecimal.valueOf(cnt), 16, java.math.RoundingMode.HALF_UP);
        BigDecimal variance = sumSq.divide(BigDecimal.valueOf(cnt), 16, java.math.RoundingMode.HALF_UP)
                .subtract(mean.multiply(mean));
        BigDecimal sampleVariance = variance.multiply(BigDecimal.valueOf(cnt))
                .divide(BigDecimal.valueOf(cnt - 1), 8, java.math.RoundingMode.HALF_UP);
        if (isSqrt) {
            return BigDecimal.valueOf(Math.sqrt(sampleVariance.doubleValue()))
                    .setScale(8, java.math.RoundingMode.HALF_UP).toPlainString();
        }
        return sampleVariance.toPlainString();
    }

    /**
     * 汇总表格导出时参与计算的所有字段列
     */
    public static List<ChartViewFieldDTO> getAllExportColumns(ChartViewDTO viewInfo) {
        List<ChartViewFieldDTO> allColumns = new ArrayList<>();
        allColumns.addAll(viewInfo.getXAxis());
        allColumns.addAll(viewInfo.getYAxis());
        allColumns.addAll(viewInfo.getXAxisExt());
        allColumns.addAll(viewInfo.getYAxisExt());
        allColumns.addAll(viewInfo.getExtStack());
        return allColumns;
    }

    /**
     * 汇总行的字段配置
     */
    public static class SummaryConfig {
        public String summaryLabel;
        public Map<String, String> summaryTypeMap = new HashMap<>();
        public Map<String, Boolean> summaryShowMap = new HashMap<>();
    }

    /**
     * 分页导出过程中的汇总统计累加器
     */
    public static class SummaryAccumulator {
        public long totalCount = 0;
        public Map<String, BigDecimal> sumMap = new HashMap<>();
        public Map<String, BigDecimal> maxMap = new HashMap<>();
        public Map<String, BigDecimal> minMap = new HashMap<>();
        public Map<String, Long> countMap = new HashMap<>();
        public Map<String, BigDecimal> sumOfSquaresMap = new HashMap<>();
    }

}
