package io.crest.ai.service;

import io.crest.api.ai.AiComponentApi;
import io.crest.commons.utils.UrlTestUtils;
import io.crest.system.manage.SysParameterManage;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 提供智能组件相关的基础配置查询能力。
 */
@RestController
@RequestMapping("ai-base")
@ConditionalOnProperty(prefix = "crest.feature.ai", name = "enabled", havingValue = "true")
public class AiBaseService implements AiComponentApi {
    @Resource
    private SysParameterManage sysParameterManage;

    @Override
    public Map<String, String> targetUrl() {
        Map<String, String> templateParams = sysParameterManage.groupVal("ai.");
        if (templateParams != null && StringUtils.isNotEmpty(templateParams.get("ai.baseUrl"))) {
            return templateParams;

        } else {
            return new HashMap<>();
        }
    }
}
