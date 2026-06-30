package io.crest.api.sync.datasource.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.sync.datasource.dto.DBTableDTO;
import io.crest.api.sync.datasource.dto.DatasourceGridRequest;
import io.crest.api.sync.datasource.dto.GetDatasourceRequest;
import io.crest.api.sync.datasource.dto.SyncDatasourceDTO;
import io.crest.api.sync.datasource.vo.SyncDatasourceVO;
import io.crest.auth.CrestApiPath;
import io.crest.exception.CrestException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.SYNC_DATASOURCE;

/**
 * 数据同步数据源管理接口。
 */
@CrestApiPath(value = "/sync/datasource", rt = SYNC_DATASOURCE)
public interface SyncDatasourceApi {

    @PostMapping("/source/page/{goPage}/{pageSize}")
    IPage<SyncDatasourceVO> sourcePager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody DatasourceGridRequest request);

    @PostMapping("/target/page/{goPage}/{pageSize}")
    IPage<SyncDatasourceVO> targetPager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody DatasourceGridRequest request);

    @PostMapping("/record")
    void save(@RequestBody SyncDatasourceDTO dataSourceDTO) throws CrestException;

    @PutMapping
    Map<String, Object> update(@RequestBody SyncDatasourceDTO dataSourceDTO) throws CrestException;

    @DeleteMapping("/{datasourceId}")
    void delete(@PathVariable("datasourceId") String datasourceId) throws CrestException;

    @GetMapping("/types")
    Object datasourceTypes() throws CrestException;

    @PostMapping("/validate")
    String validate(@RequestBody SyncDatasourceDTO dataSourceDTO) throws CrestException;

    @PostMapping("/schemas")
    List<String> getSchema(@RequestBody SyncDatasourceDTO dataSourceDTO) throws CrestException;

    @GetMapping("/validate/{datasourceId}")
    SyncDatasourceDTO validate(@PathVariable("datasourceId") String datasourceId) throws CrestException;

    @PostMapping("/latest-use/{sourceType}")
    List<String> latestUse(@PathVariable("sourceType") String sourceType);

    @GetMapping("/detail/{datasourceId}")
    SyncDatasourceDTO get(@PathVariable("datasourceId") String datasourceId) throws CrestException;

    @DeleteMapping("/batch")
    void batchDel(@RequestBody List<String> ids) throws CrestException;

    @PostMapping("/fields")
    Map<String, Object> getFields(@RequestBody GetDatasourceRequest getDsRequest) throws CrestException;

    @GetMapping("/list/{type}")
    List<SyncDatasourceDTO> listByType(@PathVariable("type") String type) throws CrestException;

    @GetMapping("/table/list/{dsId}")
    List<DBTableDTO> getTableList(@PathVariable("dsId") String dsId) throws CrestException;

    @GetMapping("/root-path/{id}")
    String query2Root(@PathVariable("id") Long id);

    @GetMapping("/log-resource/{id}")
    Long getLogResourceId(@PathVariable("id") String id);


}
