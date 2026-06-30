package io.crest.rsa.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 系统加密密钥实体。
 */
@TableName("core_crypto_key")
public class CoreRsa implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 密钥记录主键
     */
    private Integer id;

    /**
     * RSA 私钥内容
     */
    private String privateKey;

    /**
     * RSA 公钥内容
     */
    private String publicKey;

    /**
     * AES 对称加密密钥
     */
    private String aesKey;

    /**
     * SM2 私钥内容
     */
    private String sm2PrivateKey;

    /**
     * SM2 公钥内容
     */
    private String sm2PublicKey;

    /**
     * SM2 密钥生成时间
     */
    private Long sm2CreateTime;

    /**
     * RSA 密钥生成时间
     */
    private Long createTime;

    /**
     * 获取密钥记录主键
     *
     * @return 密钥记录主键
     */
    public Integer getId() {
        return id;
    }

    /**
     * 设置密钥记录主键
     *
     * @param id 密钥记录主键
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * 获取 RSA 私钥内容
     *
     * @return RSA 私钥内容
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * 设置 RSA 私钥内容
     *
     * @param privateKey RSA 私钥内容
     */
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * 获取 RSA 公钥内容
     *
     * @return RSA 公钥内容
     */
    public String getPublicKey() {
        return publicKey;
    }

    /**
     * 设置 RSA 公钥内容
     *
     * @param publicKey RSA 公钥内容
     */
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * 获取 RSA 密钥生成时间
     *
     * @return RSA 密钥生成时间
     */
    public Long getCreateTime() {
        return createTime;
    }

    /**
     * 设置 RSA 密钥生成时间
     *
     * @param createTime RSA 密钥生成时间
     */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取 AES 对称加密密钥
     *
     * @return AES 对称加密密钥
     */
    public String getAesKey() {
        return aesKey;
    }

    /**
     * 设置 AES 对称加密密钥
     *
     * @param aesKey AES 对称加密密钥
     */
    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    /**
     * 获取 SM2 私钥内容
     *
     * @return SM2 私钥内容
     */
    public String getSm2PrivateKey() {
        return sm2PrivateKey;
    }

    /**
     * 设置 SM2 私钥内容
     *
     * @param sm2PrivateKey SM2 私钥内容
     */
    public void setSm2PrivateKey(String sm2PrivateKey) {
        this.sm2PrivateKey = sm2PrivateKey;
    }

    /**
     * 获取 SM2 公钥内容
     *
     * @return SM2 公钥内容
     */
    public String getSm2PublicKey() {
        return sm2PublicKey;
    }

    /**
     * 设置 SM2 公钥内容
     *
     * @param sm2PublicKey SM2 公钥内容
     */
    public void setSm2PublicKey(String sm2PublicKey) {
        this.sm2PublicKey = sm2PublicKey;
    }

    /**
     * 获取 SM2 密钥生成时间
     *
     * @return SM2 密钥生成时间
     */
    public Long getSm2CreateTime() {
        return sm2CreateTime;
    }

    /**
     * 设置 SM2 密钥生成时间
     *
     * @param sm2CreateTime SM2 密钥生成时间
     */
    public void setSm2CreateTime(Long sm2CreateTime) {
        this.sm2CreateTime = sm2CreateTime;
    }

    /**
     * 返回密钥记录的调试字符串
     *
     * @return 密钥记录的字符串表示
     */
    @Override
    public String toString() {
        return "CoreRsa{" +
        "id = " + id +
        ", privateKey = " + privateKey +
        ", publicKey = " + publicKey +
        ", createTime = " + createTime +
        "}";
    }
}
