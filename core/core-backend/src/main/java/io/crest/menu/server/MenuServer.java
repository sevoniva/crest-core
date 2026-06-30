package io.crest.menu.server;

import io.crest.api.menu.MenuApi;
import io.crest.api.menu.vo.MenuVO;
import io.crest.menu.dao.auto.entity.CoreMenu;
import io.crest.menu.manage.MenuManage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/menu")
// 实现接口服务，衔接业务处理和返回结果
public class MenuServer implements MenuApi {

    @Resource
    private MenuManage menuManage;

    @Override
    // 查询当前业务数据列表
    public List<MenuVO> query() {
        List<CoreMenu> coreMenus = menuManage.coreMenus();
        return menuManage.query(new ArrayList<>(coreMenus));
    }
}
