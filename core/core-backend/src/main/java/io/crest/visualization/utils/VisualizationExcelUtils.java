package io.crest.visualization.utils;

import io.crest.constant.FieldTypeConstants;
import io.crest.storage.LocalStorageService;
import io.crest.storage.StorageService;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.ConfigUtils;
import io.crest.utils.LogUtil;
import io.crest.visualization.bo.ExcelSheetModel;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
// 提供当前模块复用的工具能力
public class VisualizationExcelUtils {

    private static final String suffix = ".xlsx";
    private static final StorageService FALLBACK_STORAGE_SERVICE = new LocalStorageService();
    private static final Pattern INVALID_FILE_NAME_CHARS = Pattern.compile("[\\p{Cntrl}\\\\/:\\*\\?\\\"<>\\|]");

    public static String getBaseRoot() {
        return ConfigUtils.getConfig("crest.path.report", "/opt/crest/data/report/");
    }

    public static File exportExcel(List<ExcelSheetModel> sheets, String fileName, String folderId) throws Exception {
        AtomicReference<String> realFileName = new AtomicReference<>(fileName);
        Workbook wb = new SXSSFWorkbook();

        sheets.forEach(sheet -> {

            List<List<String>> details = sheet.getData();
            List<Integer> fieldTypes = sheet.getFiledTypes();
            details.add(0, sheet.getHeads());
            String sheetName = sheet.getSheetName();
            Pattern pattern = Pattern.compile("[\\s\\\\/:\\*\\?\\\"<>\\|]");
            Matcher matcher = pattern.matcher(sheetName);
            sheetName = matcher.replaceAll("-");
            Sheet curSheet = wb.createSheet(sheetName);
            if (StringUtils.isBlank(fileName)) {
                String cName = sheetName + suffix;
                realFileName.set(cName);
            }

            CellStyle cellStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setFontHeightInPoints((short) 12);
            font.setBold(true);
            cellStyle.setFont(font);
            cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            if (CollectionUtils.isNotEmpty(details)) {
                for (int i = 0; i < details.size(); i++) {
                    Row row = curSheet.createRow(i);
                    List<String> rowData = details.get(i);
                    if (rowData != null) {
                        for (int j = 0; j < rowData.size(); j++) {
                            Cell cell = row.createCell(j);
                            // with DataType
                            if (i > 0 && (fieldTypes.get(j).equals(FieldTypeConstants.INTEGER) || fieldTypes.get(j).equals(FieldTypeConstants.FLOAT)) && StringUtils.isNotEmpty(rowData.get(j))) {
                                cell.setCellValue(Double.valueOf(rowData.get(j)));
                            } else {
                                cell.setCellValue(rowData.get(j));
                            }
                            if (i == 0) {// 头部
                                cell.setCellStyle(cellStyle);
                                // 设置列的宽度
                                curSheet.setColumnWidth(j, 255 * 20);
                            }
                        }
                    }
                }
            }
        });
        String exportFileName = StringUtils.defaultIfBlank(realFileName.get(), "export");
        if (!Strings.CS.endsWith(exportFileName, suffix)) {
            exportFileName += suffix;
        }
        StorageService storageService = storageService();
        File result = resolveExportFile(storageService, cleanExportFileName(exportFileName), folderId);
        try (OutputStream outputStream = new BufferedOutputStream(storageService.newOutputStream(result))) {
            wb.write(outputStream);
            outputStream.flush();
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), new Throwable(e));
            throw e;
        } finally {
            wb.close();
        }
        return result;
    }

    private static File resolveExportFile(StorageService storageService, String fileName, String folderId) {
        String rootPath = getBaseRoot();
        List<String> segments = new ArrayList<>();
        if (StringUtils.isNotBlank(folderId)) {
            segments.add(folderId);
        }
        segments.add(String.valueOf(Thread.currentThread().getId()));
        storageService.ensureDirectory(rootPath, segments.toArray(new String[0]));
        segments.add(fileName);
        return storageService.resolve(rootPath, segments.toArray(new String[0]));
    }

    private static StorageService storageService() {
        StorageService storageService = CommonBeanFactory.getBean(StorageService.class);
        return storageService == null ? FALLBACK_STORAGE_SERVICE : storageService;
    }

    private static String cleanExportFileName(String fileName) {
        String cleaned = INVALID_FILE_NAME_CHARS.matcher(StringUtils.defaultIfBlank(fileName, "export")).replaceAll("-").trim();
        return StringUtils.isBlank(cleaned) || ".".equals(cleaned) || "..".equals(cleaned) ? "export" + suffix : cleaned;
    }
}
