package io.crest.config;


import io.crest.commons.utils.MybatisInterceptorConfig;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.interceptor.MybatisInterceptor;
import io.crest.interceptor.ObOracleReservedIdentifierInterceptor;
import io.crest.metadata.MetadataDbTypeResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableTransactionManagement
// 集中注册 MyBatis 插件，保持元数据库兼容逻辑在启动阶段一次性确定
public class MybatisConfig {

    @Bean
    @ConditionalOnMissingBean
    // 注册字段加解密拦截器，并在 OB Oracle 下启用应用侧主键赋值
    public MybatisInterceptor dbInterceptor(Environment environment) {
        MybatisInterceptor interceptor = new MybatisInterceptor();
        List<MybatisInterceptorConfig> configList = new ArrayList<>();
        configList.add(new MybatisInterceptorConfig(CoreEngine.class, "configuration"));
        configList.add(new MybatisInterceptorConfig(CoreDatasource.class, "configuration"));
        interceptor.setInterceptorConfigList(configList);
        interceptor.setUseAssignedKeysWithoutJdbcGeneratedKeys(
                MetadataDbTypeResolver.resolve(environment).isOracleCompatible());
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    // 仅在 OB Oracle 元数据库下改写保留字字段，普通 MySQL 路径保持原 SQL
    public ObOracleReservedIdentifierInterceptor obOracleReservedIdentifierInterceptor(Environment environment) {
        return new ObOracleReservedIdentifierInterceptor(
                MetadataDbTypeResolver.resolve(environment).isOracleCompatible());
    }
}
