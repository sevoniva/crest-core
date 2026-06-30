package io.crest.doc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
@SuppressWarnings("deprecation")
// 配置 OpenAPI 文档分组和展示信息
public class SwaggerConfig {

    @Value("${crest.version:1.0.0}")
    private String version;

    // 为 OpenAPI 标签添加排序扩展
    @Bean
    public GlobalOpenApiCustomizer orderGlobalOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getTags() != null) {
                AtomicInteger order = new AtomicInteger(1);
                openApi.getTags().forEach(tag -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("x-order", order.getAndIncrement());
                    tag.setExtensions(map);
                });
            }
        };
    }

    // 构建 OpenAPI 基础信息
    @Bean
    public OpenAPI customOpenAPI() {
        Contact contact = new Contact();
        contact.setName("Crest");
        contact.setUrl("https://github.com/sevoniva/Crest");
        return new OpenAPI()
                .info(new Info()
                        .title("Crest OpenAPI")
                        .description("Crest 接口文档，覆盖可视化资源、数据准备、数据血缘、导出中心、系统设置和权限管理。")
                        .termsOfService("https://github.com/sevoniva/Crest")
                        .contact(contact)
                        .version(version));
    }


    // 配置可视化资源接口分组
    @Bean
    public GroupedOpenApi visualizationApi() {
        return GroupedOpenApi.builder().group("1-visualization").displayName("仪表盘与数据大屏").packagesToScan("io.crest.visualization", "io.crest.share").build();
    }

    // 配置图表接口分组
    @Bean
    public GroupedOpenApi chartApi() {
        return GroupedOpenApi.builder().group("2-chart").displayName("图表").packagesToScan("io.crest.chart").build();
    }

    // 配置数据集接口分组
    @Bean
    public GroupedOpenApi datasetApi() {
        return GroupedOpenApi.builder().group("3-dataset").displayName("数据集").packagesToScan("io.crest.dataset").build();
    }

    // 配置数据源接口分组
    @Bean
    public GroupedOpenApi dsApi() {
        return GroupedOpenApi.builder().group("4-datasource").displayName("数据源").packagesToScan("io.crest.datasource").build();
    }

    // 配置数据血缘接口分组
    @Bean
    public GroupedOpenApi relationApi() {
        return GroupedOpenApi.builder().group("5-lineage").displayName("数据血缘").packagesToScan("io.crest.relation").build();
    }

    // 配置导出中心接口分组
    @Bean
    public GroupedOpenApi exportApi() {
        return GroupedOpenApi.builder().group("6-export").displayName("导出中心").packagesToScan("io.crest.exportCenter").build();
    }

    // 配置系统设置接口分组
    @Bean
    public GroupedOpenApi basicSettingApi() {
        String[] packageArray = {
                "io.crest.system",
                "io.crest.font",
                "io.crest.menu",
        };
        return GroupedOpenApi.builder().group("7-system").displayName("系统设置").packagesToScan(packageArray).build();
    }

    // 配置基础能力接口分组
    @Bean
    public GroupedOpenApi baseApi() {
        return GroupedOpenApi.builder().group("8-foundation").displayName("基础能力").packagesToScan("io.crest.base", "io.crest.resource").build();
    }

    // 配置权限管理接口分组
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder().group("9-access-control").displayName("权限管理").packagesToScan("io.crest.substitute.permissions").build();
    }

    // 配置同步管理接口分组
    @Bean
    public GroupedOpenApi syncApi() {
        return GroupedOpenApi.builder().group("10-sync").displayName("同步管理").packagesToScan("io.crest.sync.task").build();
    }


}
