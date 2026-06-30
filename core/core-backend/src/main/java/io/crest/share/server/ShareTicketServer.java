package io.crest.share.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.share.ShareTicketApi;
import io.crest.api.share.request.TicketCreator;
import io.crest.api.share.request.TicketDelRequest;
import io.crest.api.share.request.TicketSwitchRequest;
import io.crest.api.share.vo.TicketVO;
import io.crest.commons.utils.CodingUtil;
import io.crest.share.manage.ShareTicketManage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供分享票据的创建、删除、开关和查询接口
 */
@RestController
@RequestMapping("/ticket")
public class ShareTicketServer implements ShareTicketApi {

    @Resource
    private ShareTicketManage shareTicketManage;

    /**
     * 保存分享票据并返回票据标识
     */
    @Override
    @SuppressWarnings("java/xss")
    public String saveTicket(TicketCreator creator) {
        return shareTicketManage.saveTicket(creator);
    }

    /**
     * 删除指定分享票据
     */
    @Override
    public void deleteTicket(TicketDelRequest request) {
        shareTicketManage.deleteTicket(request);
    }

    /**
     * 切换分享票据的访问要求
     */
    @Override
    public void switchRequire(TicketSwitchRequest request) {
        shareTicketManage.switchRequire(request);
    }

    /**
     * 分页查询资源的分享票据
     */
    @Override
    public IPage<TicketVO> pager(Long resourceId, int goPage, int pageSize) {
        Page<TicketVO> page = new Page<>(goPage, pageSize);
        return shareTicketManage.query(resourceId, page);
    }

    /**
     * 生成临时分享票据
     */
    @Override
    public String tempTicket() {
        return CodingUtil.shortUuid();
    }

    /**
     * 查询分享票据数量限制
     */
    @Override
    public Integer limit() {
        return shareTicketManage.getLimit();
    }
}
