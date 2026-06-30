package io.crest.api.ds;


import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.ds.vo.DriveDTO;
import io.crest.api.ds.vo.DriveJarDTO;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Tag(name = "数据源管理:驱动")
@ApiSupport(order = 968)
// 定义模块接口契约和数据传输结构
public interface DatasourceDriverApi {
    /**
     * 查询数据源树
     * @param keyWord 过滤关键字
     * @return
     */
    @Operation(summary = "查询数据源树", hidden = true)
    @GetMapping("/list/{keyWord}")
    List<DatasourceDTO> query(@PathVariable("keyWord") String keyWord);

    @Operation(summary = "列表")
    @GetMapping("/list")
    List<DriveDTO> list();

    @Operation(summary = "根据数据源类型获取")
    @GetMapping("/list/{dsType}")
    List<DriveDTO> listByDsType(@PathVariable("dsType") String dsType);

    @Operation(summary = "保存")
    @PostMapping("/record")
    DriveDTO save(@RequestBody DriveDTO datasourceDrive);

    @Operation(summary = "更新")
    @PutMapping
    DriveDTO update(@RequestBody DriveDTO datasourceDrive);

    @Operation(summary = "删除")
    @DeleteMapping("/{driverId}")
    void delete(@PathVariable("driverId") String driverId);

    @Operation(summary = "获取驱动jar列表")
    @GetMapping("/driver-jars/{driverId}")
    List<DriveJarDTO> listDriverJar(@PathVariable("driverId") String driverId);

    @Operation(summary = "删除驱动jar")
    @DeleteMapping("/driver-jars/{jarId}")
    void deleteDriverJar(@PathVariable("jarId") String jarId);

    @Operation(summary = "上传驱动jar")
    @PostMapping("/driver-jars/upload")
    DriveJarDTO uploadJar(@RequestParam("driverId") String driverId, @RequestParam("jarFile") MultipartFile jarFile) throws Exception;
}
