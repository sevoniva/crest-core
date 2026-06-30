package io.crest.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.crest.metadata.MetadataDatabaseIdProvider;
import io.crest.metadata.MetadataDbType;
import io.crest.metadata.MetadataDbTypeResolver;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration

@EnableCaching
// 集中配置 MyBatis Plus 分页插件和元数据库 databaseId
public class MyBatisPlusPaginationInnerConfig {

    private final Environment environment;

    public MyBatisPlusPaginationInnerConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    // 分页插件按元数据库类型选择方言，避免 OB Oracle 复用 MySQL 分页语法
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MetadataDbType metadataDbType = MetadataDbTypeResolver.resolve(environment);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(metadataDbType.getMybatisPlusDbType()));
        return interceptor;
    }

    @Bean
    // 向 MyBatis 暴露 databaseId，支持 Mapper XML 中的元数据库条件分支
    public DatabaseIdProvider databaseIdProvider() {
        return new MetadataDatabaseIdProvider(environment);
    }

}
