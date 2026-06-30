package io.crest.api.free;

import io.crest.api.free.dto.*;
import io.crest.api.free.vo.FreeRelationVO;
import io.crest.api.free.vo.FreeVO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Hidden
// 定义模块接口契约和数据传输结构
public interface FreeApi {

    @PostMapping("/list")
    List<FreeVO> query(@RequestBody FreeQueryRequest request);

    @PostMapping("/sync/all")
    void syncAll(@RequestBody FreeSyncRequest request);

    @DeleteMapping("/all")
    void deleteAll();

    @PostMapping("/sync/batch")
    void syncBatch(@RequestBody FreeBatchSyncRequest request);

    @DeleteMapping("/batch")
    void deleteBatch(@RequestBody FreeBatchDelRequest request);

    @PostMapping("/relation")
    FreeRelationVO relation(@RequestBody FreeRelationRequest request);
}
