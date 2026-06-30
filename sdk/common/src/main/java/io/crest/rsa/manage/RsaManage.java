package io.crest.rsa.manage;

import io.crest.model.RSAModel;
import io.crest.rsa.dao.entity.CoreRsa;
import io.crest.rsa.dao.mapper.CoreRsaMapper;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.CryptoMode;
import io.crest.utils.SmCryptoUtils;
import io.crest.utils.RsaUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import static io.crest.constant.CacheConstant.CommonCacheConstant.RSA_CACHE;

@Component
/**
 * RSA 与国密密钥初始化和查询管理器
 */
public class RsaManage {

    /**
     * RSA 密钥持久化访问对象
     */
    @Resource
    private CoreRsaMapper coreRsaMapper;


    /**
     * 检查系统密钥是否存在，并在国密模式下补齐 SM2 密钥
     */
    public void check() {
        RsaManage proxy = proxy();
        if (ObjectUtils.isEmpty(proxy.query())) {
            proxy.save();
        }
        if (CryptoMode.isSmSuite()) {
            SmCryptoUtils.validateConfiguration();
            proxy.ensureSmSuiteKey();
        }
    }

    /**
     * 生成并保存默认 RSA/AES 密钥
     */
    @CacheEvict(value = RSA_CACHE, key = "'crest-rsa-key'")
    public void save() {
        RSAModel model = RsaUtils.generate();
        CoreRsa coreRsa = new CoreRsa();
        coreRsa.setId(1);
        coreRsa.setCreateTime(System.currentTimeMillis());
        coreRsa.setPrivateKey(model.getPrivateKey());
        coreRsa.setPublicKey(model.getPublicKey());
        coreRsa.setAesKey(model.getAesKey());
        coreRsaMapper.insert(coreRsa);
    }

    /**
     * 确保国密模式所需的 SM2 密钥已生成
     */
    @CacheEvict(value = RSA_CACHE, key = "'crest-rsa-key'")
    public CoreRsa ensureSmSuiteKey() {
        CoreRsa coreRsa = coreRsaMapper.selectById(1);
        if (ObjectUtils.isEmpty(coreRsa)) {
            save();
            coreRsa = coreRsaMapper.selectById(1);
        }
        if (StringUtils.isBlank(coreRsa.getSm2PrivateKey()) || StringUtils.isBlank(coreRsa.getSm2PublicKey())) {
            SmCryptoUtils.Sm2KeyPair keyPair = SmCryptoUtils.generateSm2KeyPair();
            CoreRsa update = new CoreRsa();
            update.setId(coreRsa.getId());
            update.setSm2PrivateKey(SmCryptoUtils.encryptSm2PrivateKeyForStorage(keyPair.privateKey()));
            update.setSm2PublicKey(keyPair.publicKey());
            update.setSm2CreateTime(System.currentTimeMillis());
            coreRsaMapper.updateById(update);
            coreRsa = coreRsaMapper.selectById(1);
        }
        return coreRsa;
    }

    /**
     * 查询系统密钥配置
     */
    @Cacheable(value = RSA_CACHE, key = "'crest-rsa-key'", unless = "#result == null")
    public CoreRsa query() {
        return coreRsaMapper.selectById(1);
    }

    /**
     * 获取 Spring 代理对象，确保缓存注解生效
     */
    private RsaManage proxy() {
        return CommonBeanFactory.getBean(RsaManage.class);
    }
}
