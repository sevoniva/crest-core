package io.crest.metadata;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 根据 Crest 元数据库配置向 MyBatis 提供 databaseId
 */
public class MetadataDatabaseIdProvider implements DatabaseIdProvider {

    private final Environment environment;

    public MetadataDatabaseIdProvider(Environment environment) {
        this.environment = environment;
    }

    // MyBatis 传入的属性不作为数据源判定依据，统一读取 Spring 环境配置
    @Override
    public void setProperties(Properties p) {
        // 配置统一从 Spring Environment 读取
    }

    // 返回当前元数据库标识，让 Mapper XML 中的 databaseId 分支生效
    @Override
    public String getDatabaseId(DataSource dataSource) throws SQLException {
        return MetadataDbTypeResolver.resolve(environment).getDatabaseId();
    }
}
