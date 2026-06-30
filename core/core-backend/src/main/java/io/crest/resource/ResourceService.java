package io.crest.resource;

import io.crest.api.permissions.auth.dto.BusiPerCheckDTO;
import io.crest.constant.AuthEnum;
import io.crest.system.manage.CorePermissionManage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ResourceService {
    @Resource
    private CorePermissionManage corePermissionManage;

    // 计算权限信息并返回校验结果
    public boolean checkPermission(Long id) {
        BusiPerCheckDTO dto = new BusiPerCheckDTO();
        dto.setId(id);
        dto.setAuthEnum(AuthEnum.READ);
        boolean b;
        try {
            b = corePermissionManage.checkAuth(dto);
        } catch (Exception e) {
            b = false;
        }
        return b;
    }
}
