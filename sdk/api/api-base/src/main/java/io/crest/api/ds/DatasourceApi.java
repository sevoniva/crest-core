package io.crest.api.ds;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.ds.vo.*;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.ApiDefinition;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.DATASOURCE;

@Tag(name = "数据源管理:基础")
@ApiSupport(order = 969)
@CrestApiPath(value = "/datasource", rt = DATASOURCE)
// 定义模块接口契约和数据传输结构
public interface DatasourceApi {
    /**
     * 查询数据源树
     *
     * @param keyWord 过滤关键字
     * @return
     */
    @GetMapping("/list/{keyWord}")
    @Operation(summary = "查询")
    List<DatasourceDTO> query(@PathVariable("keyWord") String keyWord);

    @PostMapping("/record")
    @Operation(summary = "保存")
    DatasourceDTO save(@RequestBody BusiDsRequest dataSourceDTO) throws CrestException;

    @PutMapping
    @Operation(summary = "更新")
    DatasourceDTO update(@RequestBody BusiDsRequest dataSourceDTO) throws CrestException;

    @PostMapping("/move")
    @Operation(summary = "移动")
    DatasourceDTO move(@RequestBody BusiCreateFolderRequest dataSourceDTO) throws CrestException;

    @PostMapping("/rename")
    @Operation(summary = "重命名")
    DatasourceDTO reName(@RequestBody BusiRenameRequest dataSourceDTO) throws CrestException;

    @PostMapping("/folders")
    @Operation(summary = "新建文件夹")
    DatasourceDTO createFolder(@RequestBody BusiCreateFolderRequest dataSourceDTO) throws CrestException;

    @PostMapping("/duplicate-check")
    @Operation(summary = "校验重复")
    boolean checkRepeat(@RequestBody BusiDsRequest dataSourceDTO) throws CrestException;

    @GetMapping("/types")
    @Operation(summary = "数据源类型")
    List<DatasourceConfiguration.DatasourceType> datasourceTypes() throws CrestException;

    @PostMapping("/validate")
    @Operation(summary = "校验")
    DatasourceDTO validate(@RequestBody BusiDsRequest dataSourceDTO) throws CrestException;

    @PostMapping("/schemas")
    @Operation(summary = "获取 schema")
    List<String> getSchema(@RequestBody BusiDsRequest dataSourceDTO) throws CrestException;

    @CrestPermit({"#p0+':manage'"})
    @GetMapping("/validate/{datasourceId}")
    @Operation(summary = "校验")
    DatasourceDTO validate(@PathVariable("datasourceId") Long datasourceId) throws CrestException;

    @CrestPermit({"#p0+':manage'"})
    @PostMapping("/deletion-impact/{datasourceId}")
    @Operation(summary = "是否有数据集正在使用此数据源")
    boolean perDelete(@PathVariable("datasourceId") Long datasourceId);

    @CrestPermit({"#p0+':manage'"})
    @DeleteMapping("/{datasourceId}")
    @Operation(summary = "删除")
    void delete(@PathVariable("datasourceId") Long datasourceId) throws CrestException;

    @CrestPermit({"#p0+':manage'"})
    @GetMapping("/detail/{datasourceId}")
    @Operation(summary = "数据源详情")
    DatasourceDTO get(@PathVariable("datasourceId") Long datasourceId) throws CrestException;

    @CrestPermit({"#p0+':manage'"})
    @GetMapping("/password/masked/{datasourceId}")
    @Operation(summary = "数据源详情")
    DatasourceDTO hidePw(@PathVariable("datasourceId") Long datasourceId) throws CrestException;

    @CrestPermit({"#p0+':read'"})
    @GetMapping("/simple-info/{datasourceId}")
    @Operation(summary = "数据源详情")
    DatasourceDTO getSimpleDs(@PathVariable("datasourceId") Long datasourceId) throws CrestException;


    @PostMapping("/table-fields")
    @Operation(summary = "获取表字段")
    List<TableField> getTableField(@RequestBody Map<String, String> req) throws CrestException;

    @PostMapping("/api-tables/sync")
    @Operation(summary = "同步API数据表")
    void syncApiTable(@RequestBody Map<String, String> req) throws CrestException;

    @PostMapping("/api-data-sources/sync")
    @Operation(summary = "同步API数据源")
    void syncApiDs(@RequestBody Map<String, String> req) throws Exception;

    @PostMapping("tree")
    @Operation(summary = "数据源列表")
    List<BusiNodeVO> tree(@RequestBody BusiNodeRequest request) throws CrestException;


    @CrestPermit({"#p0.datasourceId+':read'"})
    @PostMapping("tables")
    @Operation(summary = "获取表")
    List<DatasetTableDTO> tables(@RequestBody DatasetTableDTO datasetTableDTO) throws CrestException;

    @CrestPermit({"#p0.datasourceId+':read'"})
    @PostMapping("table-status")
    @Operation(summary = "获取数据表更新状态")
    List<DatasetTableDTO> tableStatus(@RequestBody DatasetTableDTO datasetTableDTO) throws CrestException;

    @PostMapping("/api-data-source-check")
    @Operation(summary = "校验API数据源")
    ApiDefinition checkApiDatasource(@RequestBody Map<String, String> data) throws CrestException;

    @PostMapping("/files/upload")
    @Operation(summary = "上传文件")
    ExcelFileData uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("id") long datasourceId, @RequestParam("editType") Integer editType) throws CrestException;

    @PostMapping("/preview-data")
    @Operation(summary = "预览数据")
    Map<String, Object> previewDataWithLimit(@RequestBody Map<String, Object> req) throws CrestException;

    @CrestPermit({"#p0.datasourceId+':read'"})
    @PostMapping("/excel-data/page")
    @Operation(summary = "分页读取 Excel 数据")
    ExcelDataPageVO excelDataPage(@RequestBody ExcelDataPageRequest request) throws CrestException;

    @CrestPermit({"#p0.datasourceId+':manage'"})
    @PostMapping("/excel-data/record")
    @Operation(summary = "保存 Excel 在线编辑数据")
    void saveExcelData(@RequestBody ExcelDataSaveRequest request) throws CrestException;

    @PostMapping("/latest-use")
    @Operation(summary = "最近常用")
    public List<String> latestUse();

    @GetMapping("finish-page/visible")
    @Operation(summary = "是否显示完成页面")
    public boolean showFinishPage() throws CrestException;

    @PostMapping("finish-page/visible")
    @Operation(summary = "是否显示完成页面")
    public void setShowFinishPage() throws CrestException;

    @PostMapping("/sync-records/{dsId}/{goPage}/{pageSize}")
    @Operation(summary = "更新日志")
    IPage<CoreDatasourceTaskLogDTO> listSyncRecord(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @PathVariable("dsId") Long dsId);

    DatasourceDTO innerGet(Long datasourceId) throws CrestException;

    String getName(Long datasourceId) throws CrestException;

    List<DatasourceDTO> innerList(List<Long> ids, List<String> types) throws CrestException;

    @GetMapping("/simple/{id}")
    DsSimpleVO simple(@PathVariable("id") Long id);

    @PostMapping("/multidimensional-tables")
    @Operation(summary = "获取多维表格列表")
    List<Map<String, String>> multidimensionalTables(@RequestBody Map<String, String> data) throws CrestException;

    @PostMapping("/remote-files/load")
    @Operation(summary = "加载文件")
    ExcelFileData loadRemoteFile(@RequestBody RemoteExcelRequest remoteExcelRequeste) throws CrestException, IOException;
}
