package io.crest.api.visualization;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.report.bo.DatasetPermissionTemplate;
import io.crest.api.visualization.dto.VisualizationViewTableDTO;
import io.crest.api.visualization.request.DataVisualizationBaseRequest;
import io.crest.api.visualization.request.VisualizationAppExportRequest;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.visualization.vo.DataVisualizationVO;
import io.crest.api.visualization.vo.VisualizationExport2AppVO;
import io.crest.api.visualization.vo.VisualizationResourceVO;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.PANEL;

@Tag(name = "可视化管理:基础")
@ApiSupport(order = 999)
@CrestApiPath(value = "/data-visualization", rt = PANEL)
// 定义模块接口契约和数据传输结构
public interface DataVisualizationApi {
    /**
     * 查询数据可视化大屏
     *
     * @return
     */
    @PostMapping("/detail")
    @CrestPermit(value = {"#p0.id+':read'"}, busiFlag = "#p0.busiFlag")
    @Operation(summary = "查询可视化资源")
    DataVisualizationVO findById(@RequestBody DataVisualizationBaseRequest request);


    @GetMapping("/copy-resource/{dvId}/{busiFlag}")
    @Operation(summary = "查询临时复制资源")
    DataVisualizationVO findCopyResource(@PathVariable("dvId") Long dvId, @PathVariable("busiFlag") String busiFlag);


    @PostMapping("/canvas")
    @CrestPermit(value = {"#p0.pid + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "画布保存")
    String saveCanvas(@RequestBody DataVisualizationBaseRequest request) throws Exception;

    @PostMapping("/canvas-name-check")
    @Operation(summary = "应用名称检查")
    String appCanvasNameCheck(@RequestBody DataVisualizationBaseRequest request) throws Exception;

    @PostMapping("/canvas-change-impact")
    @CrestPermit(value = {"#p0.id + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "画布变动校验")
    String checkCanvasChange(@RequestBody DataVisualizationBaseRequest request);


    @PutMapping("/canvas")
    @CrestPermit(value = {"#p0.id + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "画布更新")
    DataVisualizationVO updateCanvas(@RequestBody DataVisualizationBaseRequest request);


    @PostMapping("/publish-status")
    @CrestPermit(value = {"#p0.id + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "发布状态更新")
    void updatePublishStatus(@RequestBody DataVisualizationBaseRequest request);

    @PostMapping("/published-state/recover")
    @CrestPermit(value = {"#p0.id + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "恢复到发布状态")
    void recoverToPublished(@RequestBody DataVisualizationBaseRequest request);

    @PutMapping("/base")
    @CrestPermit(value = {"#p0.id + ':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "可视化资源基础信息更新")
    void updateBase(@RequestBody DataVisualizationBaseRequest request);

    @DeleteMapping("/trash/{dvId}/{busiFlag}")
    @CrestPermit(value = {"#p0+':manage'"}, busiFlag = "#p1")
    @Operation(summary = "可视化资源删除")
    void deleteLogic(@PathVariable("dvId") Long dvId, @PathVariable("busiFlag") String busiFlag);

    @PostMapping("/tree")
    @Operation(summary = "查询可视化资源树")
    List<BusiNodeVO> tree(@RequestBody BusiNodeRequest request);

    @PostMapping("/interactive-tree")
    @Operation(summary = "查询业务资源树")
    Map<String, List<BusiNodeVO>> interactiveTree(@RequestBody Map<String, BusiNodeRequest> requestMap);

    @PostMapping("/move")
    @CrestPermit(value = {"#p0.id+':manage'", "#p0.pid+':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "移动可视化资源")
    void move(@RequestBody DataVisualizationBaseRequest request);

    @PostMapping("/name-check")
    @Operation(summary = "名称校验")
    void nameCheck(@RequestBody DataVisualizationBaseRequest request);

    @PostMapping("/recent")
    @Operation(summary = "查询最近操作资源")
    List<VisualizationResourceVO> findRecent(@RequestBody VisualizationWorkbranchQueryRequest request);

    @PostMapping("/copy")
    @JsonSerialize(using = ToStringSerializer.class)
    @CrestPermit(value = {"#p0.id+':manage'", "#p0.pid+':manage'"}, busiFlag = "#p0.type")
    @Operation(summary = "复制")
    String copy(@RequestBody DataVisualizationBaseRequest request);

    @GetMapping("/type/{dvId}")
    @Operation(summary = "查询可视化资源类型")
    String findDvType(@PathVariable("dvId") Long dvId);


    @GetMapping("/check-version/{dvId}")
    @Operation(summary = "更新校验版本")
    String updateCheckVersion(@PathVariable("dvId") Long dvId);


    /**
     * 从模板解压可视化资源 模板来源包括 模板市场、内部模板管理
     *
     * @return
     */
    @PostMapping("/decompression")
    @Operation(summary = "解析可视化资源模板信息")
    DataVisualizationVO decompression(@RequestBody DataVisualizationBaseRequest request) throws Exception;

    /**
     * 从模板解压可视化资源 模板来源包括本地上传
     *
     * @return
     */
    @PostMapping("/decompression/local-file")
    @Operation(summary = "解析可视化资源模板文件信息")
    DataVisualizationVO decompressionLocalFile(@RequestPart(value = "file") MultipartFile file);


    @GetMapping("/view-detail-list/{dvId}")
    @Operation(summary = "仪表板视图明细数据")
    List<VisualizationViewTableDTO> detailList(@PathVariable("dvId") Long dvId);

    @PostMapping("/app-export-check")
    @Operation(summary = "仪表板视图明细数据")
    VisualizationExport2AppVO export2AppCheck(@RequestBody VisualizationAppExportRequest appExportRequest);


    @PostMapping("/export-logs/app")
    @Operation(summary = "导出应用模板日志记录")
    void exportLogApp(@RequestBody DataVisualizationBaseRequest request) throws Exception;


    @PostMapping("/export-logs/template")
    @Operation(summary = "导出样式模板日志记录")
    void exportLogTemplate(@RequestBody DataVisualizationBaseRequest request) throws Exception;



    @PostMapping("/export-logs/pdf")
    @Operation(summary = "导出PDF日志记录")
    void exportLogPDF(@RequestBody DataVisualizationBaseRequest request) throws Exception;


    @PostMapping("/export-logs/image")
    @Operation(summary = "导出图片日志记录")
    void exportLogImg(@RequestBody DataVisualizationBaseRequest request) throws Exception;


    @Hidden
    List<DatasetPermissionTemplate> queruDatasetPermissionTemplate(Long resourceId);
}
