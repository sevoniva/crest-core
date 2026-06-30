package io.crest.extensions.datasource.plugin;

import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.plugins.template.CrestPlugin;
import io.crest.plugins.vo.CrestPluginVO;
import io.crest.utils.FileUtils;
import io.crest.utils.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@SuppressWarnings("deprecation")
/**
 * 数据源插件基类，负责插件注册和驱动 Jar 装载
 */
public abstract class CrestDatasourcePlugin extends Provider implements CrestPlugin {
    /**
     * 插件驱动默认释放目录
     */
    private final String DEFAULT_FILE_PATH = "/opt/crest/drivers/plugin";


    /**
     * 插件默认不返回 schema 列表，由具体插件按需覆盖
     */
    @Override
    public List<String> getSchema(DatasourceRequest datasourceRequest) {
        return new ArrayList<>();
    }


    /**
     * 加载插件配置并释放插件内置驱动
     */
    @Override
    public void loadPlugin() {
        PluginDatasourceVO datasourceConfig = getConfig();
        ProviderFactory.loadPlugin(datasourceConfig.getType(), this);
        try {
            loadDriver();
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }

    /**
     * 从插件 Jar 中释放内嵌驱动 Jar 到本地目录
     */
    @SuppressWarnings("java/zipslip")
    private void loadDriver() throws Exception {
        PluginDatasourceVO config = getConfig();
        String localPath = StringUtils.isEmpty(config.getDriverPath()) ? DEFAULT_FILE_PATH : config.getDriverPath();
        ProtectionDomain protectionDomain = this.getClass().getProtectionDomain();
        URI uri = protectionDomain.getCodeSource().getLocation().toURI();
        try (JarFile jarFile = new JarFile(new File(uri))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (Strings.CS.endsWith(name, ".jar")) {
                    File file = FileUtils.resolveUnderDirectory(localPath, Paths.get(name).getFileName().toString());

                    try (InputStream inputStream = jarFile.getInputStream(entry);
                         FileOutputStream outputStream = new FileOutputStream(file)) {
                        byte[] bytes = new byte[1024];
                        int length;
                        while ((length = inputStream.read(bytes)) >= 0) {
                            outputStream.write(bytes, 0, length);
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析插件元信息中的数据源配置
     */
    public PluginDatasourceVO getConfig() {
        CrestPluginVO pluginInfo = null;
        try {
            pluginInfo = getPluginInfo();
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        String config = pluginInfo.getConfig();
        PluginDatasourceVO vo = JsonUtil.parseObject(config, PluginDatasourceVO.class);
        vo.setIcon(pluginInfo.getIcon());
        return vo;
    }

    /**
     * 卸载插件时删除释放到默认目录的驱动 Jar
     */
    @Override
    @SuppressWarnings("java/zipslip")
    public void unloadPlugin() {
        try {
            ProtectionDomain protectionDomain = this.getClass().getProtectionDomain();
            URI uri = protectionDomain.getCodeSource().getLocation().toURI();
            try (JarFile jarFile = new JarFile(new File(uri))) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (Strings.CS.endsWith(name, ".jar")) {
                        File file = FileUtils.resolveUnderDirectory(DEFAULT_FILE_PATH, Paths.get(name).getFileName().toString());
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }
}
