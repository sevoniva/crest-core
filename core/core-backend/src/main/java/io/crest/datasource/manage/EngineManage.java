package io.crest.datasource.manage;

import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.dao.auto.mapper.CoreEngineMapper;
import io.crest.datasource.type.ObOracle;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.result.ResultMessage;
import io.crest.utils.BeanUtils;
import io.crest.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@Transactional(rollbackFor = Exception.class)
@SuppressWarnings("unchecked")
// 管理数据引擎配置、校验和内置演示数据源初始化
public class EngineManage {

    @Resource
    private Environment env;
    @Resource
    private CoreEngineMapper engineMapper;

    // 查询当前数据引擎配置
    public CoreEngine info() throws CrestException {
        List<CoreEngine> engines = engineMapper.selectList(null);
        if (CollectionUtils.isEmpty(engines)) {
            CrestException.throwException("未完整设置数据引擎");
        }
        return engines.get(0);
    }

    // 将当前引擎配置转换为数据源实体
    public CoreDatasource getEngineDatasource() {
        List<CoreEngine> engines = engineMapper.selectList(null);
        if (CollectionUtils.isEmpty(engines)) {
            CrestException.throwException("未完整设置数据引擎");
        }
        CoreDatasource coreDatasource = new CoreDatasource();
        BeanUtils.copyBean(coreDatasource, engines.get(0));
        return coreDatasource;
    }


    // 查询当前引擎对应的数据源实体
    public CoreDatasource engineDatasource() {
        List<CoreEngine> engines = engineMapper.selectList(null);
        CoreDatasource coreDatasource = new CoreDatasource();
        if (CollectionUtils.isEmpty(engines)) {
            return null;
        }
        BeanUtils.copyBean(coreDatasource, engines.get(0));
        return coreDatasource;
    }

    // 校验引擎连接配置
    public void validate(CoreEngine engine) throws Exception {
        assertObOracleEngine(engine.getType());
        if (StringUtils.isEmpty(engine.getType()) || StringUtils.isEmpty(engine.getConfiguration())) {
            throw new Exception("未完整设置数据引擎");
        }
        try {

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            DatasourceDTO datasource = new DatasourceDTO();
            BeanUtils.copyBean(datasource, engine);
            datasourceRequest.setDatasource(datasource);
            ProviderFactory.getProvider(engine.getType()).checkStatus(datasourceRequest);
        } catch (Exception e) {
            CrestException.throwException("校验失败：" + e.getMessage());
        }
    }

    // 保存数据引擎配置
    public ResultMessage save(CoreEngine engine) {
        assertObOracleEngine(engine.getType());
        if (engine.getId() == null) {
            engineMapper.insert(engine);
        } else {
            engineMapper.updateById(engine);
        }
        return ResultMessage.success(engine);
    }

    // 初始化默认本地数据引擎
    public void initSimpleEngine() throws Exception {
        String targetEngineType = "obOracle";
        List<CoreEngine> engines = engineMapper.selectList(null);
        CoreEngine current = CollectionUtils.isEmpty(engines) ? null : engines.get(0);
        if (current != null
                && targetEngineType.equalsIgnoreCase(StringUtils.defaultString(current.getType()))
                && validConfiguration(current.getConfiguration())
                && !legacyCharsetConfiguration(current.getConfiguration())) {
            return;
        }

        CoreEngine engine = current == null ? new CoreEngine() : current;
        engine.setType("obOracle");
        ObOracle obOracle = new ObOracle();
        applyJdbcUrlEngineConfig(obOracle);
        engine.setConfiguration(JsonUtil.toJSONString(obOracle).toString());
        engine.setName("默认引擎");
        engine.setDescription("默认引擎");
        if (engine.getId() == null) {
            engineMapper.insert(engine);
        } else {
            engineMapper.updateById(engine);
        }
    }

    private void applyJdbcUrlEngineConfig(io.crest.extensions.datasource.vo.DatasourceConfiguration configuration) {
        configuration.setUrlType("jdbcUrl");
        configuration.setJdbcUrl(env.getProperty("spring.datasource.url"));
        configuration.setUsername(env.getProperty("spring.datasource.username"));
        configuration.setPassword(env.getProperty("spring.datasource.password"));
        configuration.setInitialPoolSize(5);
        configuration.setMinPoolSize(5);
        configuration.setMaxPoolSize(20);
        configuration.setQueryTimeout(30);
        configuration.setUseSSH(false);
        configuration.convertJdbcUrl();
    }

    // 判断引擎配置是否为有效 JSON 配置
    private boolean validConfiguration(String configuration) {
        return StringUtils.isNotBlank(configuration) && configuration.trim().startsWith("{");
    }

    private void assertObOracleEngine(String type) {
        if (!"obOracle".equalsIgnoreCase(StringUtils.defaultString(type))) {
            CrestException.throwException("Crest Core only supports obOracle engine.");
        }
    }

    // 判断引擎配置是否仍使用旧版字符集参数
    private boolean legacyCharsetConfiguration(String configuration) {
        if (StringUtils.isBlank(configuration)) {
            return false;
        }
        String normalized = configuration.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("charactersetresults")
                || normalized.contains("characterencoding=utf8&");
    }

}
