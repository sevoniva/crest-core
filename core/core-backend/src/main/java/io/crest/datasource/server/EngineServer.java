package io.crest.datasource.server;

import io.crest.api.ds.EngineApi;
import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.dao.auto.mapper.CoreEngineMapper;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.provider.CalciteProvider;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.result.ResultCode;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.utils.IDUtils;
import io.crest.utils.RsaUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 提供引擎数据源配置的查询、保存和校验接口
 */
@RestController
@RequestMapping("/engine")
@Transactional(rollbackFor = Exception.class)
public class EngineServer implements EngineApi {
    @Resource
    private CoreEngineMapper engineMapper;
    @Resource
    private EngineManage engineManage;
    @Resource
    private CalciteProvider calciteProvider;

    /**
     * 查询当前引擎数据源配置
     */
    @Override
    public DatasourceDTO getEngine() {
        if (!AuthUtils.getUser().getUserId().equals(1L)) {
            CrestException.throwException("非管理员，无权访问！");
        }
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        List<CoreEngine> engines = engineMapper.selectList(null);
        if (CollectionUtils.isEmpty(engines)) {
            return datasourceDTO;
        }
        BeanUtils.copyBean(datasourceDTO, engines.get(0));
        datasourceDTO.setConfiguration(RsaUtils.symmetricEncrypt(datasourceDTO.getConfiguration()));
        return datasourceDTO;
    }

    /**
     * 保存引擎数据源配置并刷新计算引擎
     */
    @Override
    public void save(DatasourceDTO datasourceDTO) {
        if (!AuthUtils.getUser().getUserId().equals(1L)) {
            CrestException.throwException("非管理员，无权访问！");
        }
        if (StringUtils.isNotEmpty(datasourceDTO.getConfiguration())) {
            datasourceDTO.setConfiguration(decodeBase64RequestValue(datasourceDTO.getConfiguration(), "引擎配置"));
        }
        CoreEngine coreEngine = new CoreEngine();
        BeanUtils.copyBean(coreEngine, datasourceDTO);
        if (coreEngine.getId() == null) {
            coreEngine.setId(IDUtils.snowID());
            datasourceDTO.setId(coreEngine.getId());
        }
        engineManage.save(coreEngine);
        calciteProvider.update(datasourceDTO);
    }

    /**
     * 校验提交的引擎数据源配置
     */
    @Override
    public void validate(DatasourceDTO datasourceDTO) throws Exception {
        if (!AuthUtils.getUser().getUserId().equals(1L)) {
            CrestException.throwException("非管理员，无权访问！");
        }
        CoreEngine coreEngine = new CoreEngine();
        BeanUtils.copyBean(coreEngine, datasourceDTO);
        coreEngine.setConfiguration(decodeBase64RequestValue(coreEngine.getConfiguration(), "引擎配置"));
        engineManage.validate(coreEngine);
    }

    /**
     * 根据配置编号校验引擎数据源
     */
    @Override
    public void validateById(Long id) throws Exception {
        if (!AuthUtils.getUser().getUserId().equals(1L)) {
            CrestException.throwException("非管理员，无权访问！");
        }
        engineManage.validate(engineMapper.selectById(id));
    }

    /**
     * 判断当前引擎是否支持设置加密密钥
     */
    @Override
    public boolean supportSetKey() throws Exception {
        List<CoreEngine> engines = engineMapper.selectList(null);
        if (CollectionUtils.isEmpty(engines)) {
            return false;
        } else {
            return !engines.get(0).getType().equalsIgnoreCase("h2");
        }

    }

    /**
     * 解码前端提交的 Base64 配置字段
     */
    private static String decodeBase64RequestValue(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), fieldName + "格式无效");
            return "";
        }
    }
}
