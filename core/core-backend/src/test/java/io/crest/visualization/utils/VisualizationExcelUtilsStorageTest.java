package io.crest.visualization.utils;

import io.crest.constant.FieldTypeConstants;
import io.crest.visualization.bo.ExcelSheetModel;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisualizationExcelUtilsStorageTest {

    @TempDir
    Path reportRoot;

    @AfterEach
    void tearDown() {
        System.clearProperty("crest.path.report");
    }

    @Test
    @DisplayName("可视化导出应通过存储服务写入配置的报告目录")
    void exportExcelShouldWriteToConfiguredStoragePath() throws Exception {
        System.setProperty("crest.path.report", reportRoot.toString() + File.separator);

        File result = VisualizationExcelUtils.exportExcel(List.of(sheet("Daily Sheet")), "sales/report", "dv-1");

        assertThat(result.toPath().normalize()).startsWith(reportRoot.normalize());
        assertThat(result.getName()).isEqualTo("sales-report.xlsx");
        assertThat(result).isFile();
        try (Workbook workbook = WorkbookFactory.create(result)) {
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Daily-Sheet");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("name");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(3D);
        }
    }

    @Test
    @DisplayName("空文件名应使用工作表名且不重复追加后缀")
    void exportExcelShouldNotAppendDuplicateSuffixForBlankFileName() throws Exception {
        System.setProperty("crest.path.report", reportRoot.toString() + File.separator);

        File result = VisualizationExcelUtils.exportExcel(List.of(sheet("Daily/Sheet")), "", "dv-1");

        assertThat(result.getName()).isEqualTo("Daily-Sheet.xlsx");
    }

    private ExcelSheetModel sheet(String sheetName) {
        ExcelSheetModel sheet = new ExcelSheetModel();
        sheet.setSheetName(sheetName);
        sheet.setHeads(List.of("name", "count"));
        List<List<String>> data = new ArrayList<>();
        data.add(new ArrayList<>(List.of("alpha", "3")));
        sheet.setData(data);
        sheet.setFiledTypes(List.of(FieldTypeConstants.STRING, FieldTypeConstants.INTEGER));
        return sheet;
    }
}
