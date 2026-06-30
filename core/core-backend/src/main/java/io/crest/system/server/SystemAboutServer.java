package io.crest.system.server;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/about")
// 提供系统版本和构建标识查询接口
public class SystemAboutServer {

    @Value("${crest.version:unknown}")
    private String version;

    @Value("${crest.build.commit-id:unknown}")
    private String commitId;

    @GetMapping
    // 查询当前服务构建信息
    public SystemAboutInfo about() {
        return new SystemAboutInfo(normalize(version), normalize(commitId));
    }

    private String normalize(String value) {
        return StringUtils.defaultIfBlank(value, "unknown");
    }

    public record SystemAboutInfo(String version, String commitId) {}
}
