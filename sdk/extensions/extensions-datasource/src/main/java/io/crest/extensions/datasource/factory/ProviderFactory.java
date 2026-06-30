package io.crest.extensions.datasource.factory;

import io.crest.exception.CrestException;
import io.crest.extensions.datasource.plugin.CrestDatasourcePlugin;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.utils.SpringContextUtil;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.plugins.factory.CrestPluginFactory;
import io.crest.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据源 Provider 工厂，负责内置和插件 Provider 的查找与注册
 */
public class ProviderFactory {

    private static final String ALLOWED_DATASOURCE_TYPES_PROPERTY = "crest.datasource.allowed-types";
    private static final Set<String> DEFAULT_ALLOWED_DATASOURCE_TYPES = Set.of("obOracle", "Excel", "ExcelRemote", "API");

    /**
     * 按数据源类型获取对应 Provider
     */
    public static Provider getProvider(String type) throws CrestException {
        if (!isDatasourceTypeAllowed(type)) {
            CrestException.throwException("Datasource type not supported.");
        }
        if (type.equalsIgnoreCase("es")) {
            return SpringContextUtil.getApplicationContext().getBean("esProvider", Provider.class);
        }
        List<String> list = Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).toList();
        if (list.contains(type)) {
            return SpringContextUtil.getApplicationContext().getBean("calciteProvider", Provider.class);
        }
        Provider instance = getInstance(type);
        if (instance == null) {
            CrestException.throwException("插件异常，请检查插件");
        }
        return instance;
    }

    /**
     * 获取默认 Calcite Provider
     */
    public static Provider getDefaultProvider() {
        return SpringContextUtil.getApplicationContext().getBean("calciteProvider", Provider.class);
    }


    /**
     * 已加载插件数据源 Provider 缓存
     */
    private static final Map<String, CrestDatasourcePlugin> templateMap = new ConcurrentHashMap<>();

    /**
     * 从插件缓存中获取指定类型 Provider
     */
    public static Provider getInstance(String type) {
        String key = type;
        return templateMap.get(key);
    }

    /**
     * 注册插件数据源 Provider，并加载插件模板
     */
    public static void loadPlugin(String type, CrestDatasourcePlugin plugin) {
        if (!isDatasourceTypeAllowed(type)) {
            LogUtil.warn("Skip datasource plugin outside Crest Core scope: " + type);
            return;
        }
        String key = type;
        if (templateMap.containsKey(key)) return;
        templateMap.put(key, plugin);
        try {
            String moduleName = plugin.getPluginInfo().getModuleName();
            CrestPluginFactory.loadTemplate(moduleName, plugin);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), new Throwable(e));
            CrestException.throwException(e);
        }
    }

    /**
     * 返回所有插件数据源配置
     */
    public static List<PluginDatasourceVO> getDsConfigList() {
        return templateMap.values().stream().map(CrestDatasourcePlugin::getConfig).toList();
    }

    private static boolean isDatasourceTypeAllowed(String type) {
        if (StringUtils.isBlank(type)) {
            return false;
        }
        if ("folder".equals(type)) {
            return true;
        }
        return allowedDatasourceTypes().contains(type);
    }

    private static Set<String> allowedDatasourceTypes() {
        ApplicationContext context = SpringContextUtil.getApplicationContext();
        if (context == null) {
            return DEFAULT_ALLOWED_DATASOURCE_TYPES;
        }
        Environment environment = context.getBean(Environment.class);
        String configured = environment.getProperty(ALLOWED_DATASOURCE_TYPES_PROPERTY, "");
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_ALLOWED_DATASOURCE_TYPES;
        }
        return Arrays.stream(configured.split(","))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }
}
