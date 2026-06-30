package io.crest.extensions.datafilling.factory;

import io.crest.exception.CrestException;
import io.crest.extensions.datafilling.plugin.DataFillingPlugin;
import io.crest.extensions.datafilling.provider.ExtDDLProvider;
import io.crest.extensions.datafilling.vo.PluginDataFillingVO;
import io.crest.extensions.datasource.utils.SpringContextUtil;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.plugins.factory.CrestPluginFactory;
import io.crest.utils.LogUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据填报 DDL 提供器工厂，负责内置数据源和插件数据源的提供器查找
 */
public class ExtDDLProviderFactory {

    /**
     * 已加载的数据填报插件缓存，键名带有数据填报前缀以区分其他插件类型
     */
    private static final Map<String, DataFillingPlugin> templateMap = new ConcurrentHashMap<>();

    /**
     * 根据数据源类型获取对应的 DDL 提供器
     */
    public static ExtDDLProvider getExtDDLProvider(String type) {
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(type);
        switch (datasourceType) {
            case mysql, mariadb -> {
                return SpringContextUtil.getApplicationContext().getBean("mysqlExtDDLProvider", ExtDDLProvider.class);
            }
        }
        ExtDDLProvider instance = getInstance(type);
        if (instance == null) {
            CrestException.throwException("插件异常，请检查插件");
        }
        return instance;
    }

    /**
     * 从插件缓存中读取指定类型的数据填报提供器
     */
    public static ExtDDLProvider getInstance(String type) {
        String key = "df_" + type;
        return templateMap.get(key);
    }

    /**
     * 注册数据填报插件并加载插件模板
     */
    public static void loadPlugin(String type, DataFillingPlugin plugin) {
        String key = "df_" + type;
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
     * 获取所有已加载数据填报插件的配置列表
     */
    public static List<PluginDataFillingVO> getDfConfigList() {
        return templateMap.values().stream().map(DataFillingPlugin::getConfig).toList();
    }

}
