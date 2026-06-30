package io.crest.api.chart;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.chart.request.ChartExcelRequest;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.PANEL;

@Tag(name = "图表管理:数据")
@ApiSupport(order = 989)
@CrestApiPath(value = "/chart-data", rt = PANEL)
// 定义模块接口契约和数据传输结构
public interface ChartDataApi {
    @Operation(summary = "获取图表数据")
    @PostMapping("data")
    ChartViewDTO getData(@RequestBody ChartViewDTO chartViewDTO) throws Exception;

    @Operation(summary = "导出数据")
    @PostMapping("/internal-export/details")
    @CrestPermit(value = {"#p0.dvId+':export_view'"}, busiFlag = "#p0.busiFlag")
    void innerExportDetails(@RequestBody ChartExcelRequest request, HttpServletResponse response) throws Exception;

    @Operation(summary = "导出明细数据")
    @PostMapping("/internal-export/dataset-details")
    @CrestPermit(value = {"#p0.dvId+':export_detail'"}, busiFlag = "#p0.busiFlag")
    void innerExportDataSetDetails(@RequestBody ChartExcelRequest request, HttpServletResponse response) throws Exception;

    @Operation(summary = "获取字段值")
    @PostMapping("/field-values/{fieldId}/{fieldType}")
    List<String> getFieldData(@RequestBody ChartViewDTO view, @PathVariable Long fieldId, @PathVariable String fieldType) throws Exception;

    @Operation(summary = "获取下钻字段值")
    @PostMapping("/drill-field-values/{fieldId}")
    List<String> getDrillFieldData(@RequestBody ChartViewDTO view, @PathVariable Long fieldId) throws Exception;
}
