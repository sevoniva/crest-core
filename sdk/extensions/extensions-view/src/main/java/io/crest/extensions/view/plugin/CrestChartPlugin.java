package io.crest.extensions.view.plugin;

import io.crest.exception.CrestException;
import io.crest.extensions.view.factory.PluginsChartFactory;
import io.crest.extensions.view.vo.PluginViewVO;
import io.crest.plugins.template.CrestPlugin;
import io.crest.plugins.vo.CrestPluginVO;
import io.crest.utils.JsonUtil;

// 定义插件扩展能力的基础类型
public abstract class CrestChartPlugin extends AbstractChartPlugin implements CrestPlugin {

    @Override
    public void loadPlugin() {
        PluginViewVO viewConfig = getConfig();
        PluginsChartFactory.loadPlugin(viewConfig.getRender(), viewConfig.getChartValue(), this);
    }

    // 读取配置并返回当前功能所需参数
    public PluginViewVO getConfig() {
        CrestPluginVO pluginInfo = null;
        try {
            pluginInfo = getPluginInfo();
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        String config = pluginInfo.getConfig();
        PluginViewVO vo = JsonUtil.parseObject(config, PluginViewVO.class);
        vo.setIcon(pluginInfo.getIcon());
        return vo;
    }
}
