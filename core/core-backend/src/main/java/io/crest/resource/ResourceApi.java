package io.crest.resource;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resource")
public class ResourceApi {
    @Resource
    private ResourceService resourceService;

    @PostMapping("permission-check/{id}")
    // 计算权限信息并返回校验结果
    public boolean checkPermission(@PathVariable("id") Long id) {
        return resourceService.checkPermission(id);
    }
}
