package io.crest.home;

import io.crest.utils.ModelUtils;
import io.crest.utils.RsaUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
/**
 * 基础公开接口控制器，提供密钥和运行模式查询
 */
public class RestIndexController {

    /**
     * 返回前端加密使用的 RSA 公钥
     */
    @GetMapping("/public-key")
    @ResponseBody
    public String clientKey() {
        return RsaUtils.publicKey();
    }

    /**
     * 生成临时对称密钥
     */
    @GetMapping("/symmetric-key")
    @ResponseBody
    public String symmetricKey() {
        return RsaUtils.generateSymmetricKey();
    }


    /**
     * 返回当前是否为桌面模式
     */
    @GetMapping("/model")
    @ResponseBody
    public boolean model() {
        return ModelUtils.isDesktop();
    }

}
