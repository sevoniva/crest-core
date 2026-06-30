package io.crest.extensions.datafilling.plugin;

import io.crest.exception.CrestException;
import io.crest.extensions.datafilling.factory.ExtDDLProviderFactory;
import io.crest.extensions.datafilling.provider.ExtDDLProvider;
import io.crest.extensions.datafilling.vo.PluginDataFillingVO;
import io.crest.plugins.template.CrestPlugin;
import io.crest.plugins.vo.CrestPluginVO;
import io.crest.utils.JsonUtil;

// 定义插件扩展能力的基础类型
public abstract class DataFillingPlugin extends ExtDDLProvider implements CrestPlugin {

    @Override
    public void loadPlugin() {
        PluginDataFillingVO viewConfig = getConfig();
        ExtDDLProviderFactory.loadPlugin(viewConfig.getType(), this);
    }


    // 读取配置并返回当前功能所需参数
    public PluginDataFillingVO getConfig() {
        CrestPluginVO pluginInfo = null;
        try {
            pluginInfo = getPluginInfo();
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        String config = pluginInfo.getConfig();
        PluginDataFillingVO vo = JsonUtil.parseObject(config, PluginDataFillingVO.class);
        vo.setIcon(pluginInfo.getIcon());
        return vo;
    }

    @Override
    public void unloadPlugin() {

    }
}
