package io.crest.system.manage;

import io.crest.api.permissions.auth.dto.BusiPerCheckDTO;
import org.springframework.stereotype.Component;

@Component
// 封装当前业务的持久化和查询逻辑
public class CorePermissionManage {
    public boolean checkAuth(BusiPerCheckDTO dto) {
        return true;
    }
}
