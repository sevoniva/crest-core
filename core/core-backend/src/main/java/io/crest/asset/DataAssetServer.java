package io.crest.asset;

import io.crest.asset.dto.DataAssetDetailVO;
import io.crest.asset.dto.DataAssetImpactVO;
import io.crest.asset.dto.DataAssetOwnerVO;
import io.crest.asset.dto.DataAssetPageVO;
import io.crest.asset.dto.DataAssetProfileRequest;
import io.crest.asset.dto.DataAssetRequest;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.log.CrestAudit;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.DATA_ASSET;

@RestController
@RequestMapping("/data-assets")
@CrestApiPath(value = "/data-assets", rt = DATA_ASSET)
/**
 * 数据资产接口，提供资产检索、详情、影响分析和负责人列表
 */
public class DataAssetServer {

    /**
     * 数据资产领域服务
     */
    @Resource
    private DataAssetManage dataAssetManage;

    /**
     * 分页查询数据资产列表
     */
    @CrestAudit(ot = LogOT.READ, st = LogST.DATA)
    @CrestPermit("m:read")
    @PostMapping("/page/{page}/{pageSize}")
    public DataAssetPageVO page(@PathVariable Integer page,
                                @PathVariable Integer pageSize,
                                @RequestBody(required = false) DataAssetRequest request) {
        return dataAssetManage.page(page, pageSize, request);
    }

    /**
     * 查询指定资产详情
     */
    @CrestAudit(ot = LogOT.READ, st = LogST.DATA, id = "#p1")
    @CrestPermit("m:read")
    @GetMapping("/{assetType}/{assetId}")
    public DataAssetDetailVO detail(@PathVariable String assetType, @PathVariable String assetId) {
        return dataAssetManage.detail(assetType, assetId);
    }

    /**
     * 查询指定资产的影响范围
     */
    @CrestAudit(ot = LogOT.READ, st = LogST.DATA, id = "#p1")
    @CrestPermit("m:read")
    @GetMapping("/{assetType}/{assetId}/impact")
    public DataAssetImpactVO impact(@PathVariable String assetType, @PathVariable String assetId) {
        return dataAssetManage.impact(assetType, assetId);
    }

    /**
     * 保存资产画像信息
     */
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.DATA, id = "#p0.assetId")
    @CrestPermit("m:read")
    @PostMapping("/profile")
    public DataAssetDetailVO saveProfile(@RequestBody DataAssetProfileRequest request) {
        return dataAssetManage.saveProfile(request);
    }

    /**
     * 查询可选资产负责人列表
     */
    @CrestAudit(ot = LogOT.READ, st = LogST.USER)
    @CrestPermit("m:read")
    @GetMapping("/owners")
    public List<DataAssetOwnerVO> owners() {
        return dataAssetManage.owners();
    }
}
