package io.crest.api.ai;

import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 智能组件基础配置接口。
 */
public interface AiComponentApi {
    @GetMapping("target-url")
    Map<String, String> targetUrl();
}
