package io.crest.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 提供替代登录模式下的默认密码和令牌密钥配置
 */
@ConditionalOnMissingBean(name = "loginServer")
@Configuration
@SuppressWarnings("unchecked")
public class SubstituleLoginConfig {
    private static final String PWD_KEY = "pwd";
    private static final String TOKEN_SECRET_KEY = "tokenSecret";
    private static final String TOKEN_SECRET_PROPERTY = "crest.security.token-secret";

    @Value("${crest.path.substitule:classpath:substitule.json}")
    private String jsonFilePath;

    private static volatile String pwd;
    private static volatile String tokenSecret;
    private static volatile boolean tokenSecretExternal = false;

    private static volatile boolean ready = false;


    @ConditionalOnMissingBean(name = "loginServer")
    @Bean
    /**
     * 初始化替代登录配置数据并补齐缺省值
     */
    public Map<String, Object> substituleLoginData(ResourceLoader resourceLoader) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File jsonFile = new File(jsonFilePath);
        Map<String, Object> data = jsonFile.exists() ? objectMapper.readValue(jsonFile, Map.class) : new HashMap<>();
        boolean updated = false;
        String configuredPwd = CommonBeanFactory.getBean(Environment.class).getProperty("crest.default-pwd", "admin");
        pwd = readString(data, PWD_KEY);
        if (StringUtils.isBlank(pwd)) {
            pwd = configuredPwd;
            data.put(PWD_KEY, pwd);
            updated = true;
        }
        String externalTokenSecret = resolveExternalTokenSecret();
        tokenSecretExternal = StringUtils.isNotBlank(externalTokenSecret);
        tokenSecret = StringUtils.defaultIfBlank(externalTokenSecret, readString(data, TOKEN_SECRET_KEY));
        if (StringUtils.isBlank(tokenSecret)) {
            tokenSecret = generateSecret();
            data.put(TOKEN_SECRET_KEY, tokenSecret);
            updated = true;
        }
        if (updated) {
            writeConfig(jsonFile, data);
        }
        ready = true;
        return data;
    }

    /**
     * 获取替代登录密码
     */
    public static String getPwd() {
        if (ready && StringUtils.isNotBlank(pwd)) {
            return pwd;
        }
        synchronized (SubstituleLoginConfig.class) {
            if (ready && StringUtils.isNotBlank(pwd)) {
                return pwd;
            }
            ready = true;
            Object substituleLoginDataObject = CommonBeanFactory.getBean("substituleLoginData");
            if (substituleLoginDataObject != null) {
                Map<String, Object> substituleLoginData = (Map<String, Object>) substituleLoginDataObject;
                String configuredPwd = readString(substituleLoginData, PWD_KEY);
                if (StringUtils.isNotBlank(configuredPwd)) {
                    pwd = configuredPwd;
                }
                String configuredTokenSecret = readString(substituleLoginData, TOKEN_SECRET_KEY);
                if (StringUtils.isNotBlank(configuredTokenSecret)) {
                    tokenSecret = configuredTokenSecret;
                }
            }
            String externalTokenSecret = resolveExternalTokenSecret();
            if (StringUtils.isNotBlank(externalTokenSecret)) {
                tokenSecret = externalTokenSecret;
                tokenSecretExternal = true;
            }
            if (StringUtils.isBlank(pwd)) {
                pwd = CommonBeanFactory.getBean(Environment.class).getProperty("crest.default-pwd", "admin");
            }
            return pwd;
        }
    }

    /**
     * 获取替代登录令牌签名密钥
     */
    public static String getTokenSecret() {
        getPwd();
        String externalTokenSecret = resolveExternalTokenSecret();
        if (StringUtils.isNotBlank(externalTokenSecret)) {
            tokenSecret = externalTokenSecret;
            tokenSecretExternal = true;
            return tokenSecret;
        }
        if (StringUtils.isBlank(tokenSecret)) {
            tokenSecret = generateSecret();
        }
        return tokenSecret;
    }

    /**
     * 修改替代登录密码并写回配置文件
     */
    public void modifyPwd(String pwd) {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(jsonFilePath);
        Map<String, String> myObject = new HashMap<>();
        String fileTokenSecret = null;
        if (file.exists()) {
            try {
                fileTokenSecret = readString(objectMapper.readValue(file, Map.class), TOKEN_SECRET_KEY);
            } catch (IOException e) {
                LogUtil.error(e.getCause(), new Throwable(e));
            }
        }
        myObject.put(PWD_KEY, pwd);
        myObject.put(TOKEN_SECRET_KEY, StringUtils.defaultIfBlank(fileTokenSecret,
                StringUtils.defaultIfBlank(tokenSecretExternal ? null : tokenSecret, generateSecret())));
        SubstituleLoginConfig.pwd = pwd;
        String externalTokenSecret = resolveExternalTokenSecret();
        SubstituleLoginConfig.tokenSecret = StringUtils.defaultIfBlank(externalTokenSecret, myObject.get(TOKEN_SECRET_KEY));
        SubstituleLoginConfig.tokenSecretExternal = StringUtils.isNotBlank(externalTokenSecret);
        try {
            writeConfig(file, myObject);
        } catch (IOException e) {
            LogUtil.error(e.getCause(), new Throwable(e));
        }
    }

    /**
     * 将替代登录配置写入文件
     */
    private static void writeConfig(File file, Map<?, ?> config) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            mapper.writeValue(fos, config);
        }
    }

    /**
     * 从配置映射中读取字符串值
     */
    private static String readString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return ObjectUtils.isEmpty(value) ? null : value.toString();
    }

    private static String resolveExternalTokenSecret() {
        Environment environment = CommonBeanFactory.getBean(Environment.class);
        if (environment == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(environment.getProperty(TOKEN_SECRET_PROPERTY),
                environment.getProperty("CREST_TOKEN_SECRET"));
    }

    /**
     * 生成替代登录令牌签名密钥
     */
    private static String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
