package io.crest.share.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.share.request.TicketCreator;
import io.crest.api.share.request.TicketDelRequest;
import io.crest.api.share.request.TicketSwitchRequest;
import io.crest.api.share.vo.TicketVO;
import io.crest.api.share.vo.TicketValidVO;
import io.crest.commons.utils.CodingUtil;
import io.crest.exception.CrestException;
import io.crest.share.dao.auto.entity.CoreShareTicket;
import io.crest.share.dao.auto.entity.CoreShare;
import io.crest.share.dao.auto.mapper.CoreShareTicketMapper;
import io.crest.share.dao.auto.mapper.CoreShareMapper;
import io.crest.share.dao.ext.mapper.ShareExtMapper;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Component
// 管理分享访问口令的创建、校验和查询
public class ShareTicketManage {
    private static final int MIN_TICKET_LENGTH = 6;
    private static final int MAX_TICKET_LENGTH = 64;

    @Resource
    private CoreShareTicketMapper coreShareTicketMapper;

    @Resource
    private CoreShareMapper coreShareMapper;

    @Resource
    private ShareExtMapper shareExtMapper;


    // 按口令查询分享口令记录
    public CoreShareTicket getByTicket(String ticket) {
        QueryWrapper<CoreShareTicket> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ticket", ticket);
        return coreShareTicketMapper.selectOne(queryWrapper);
    }

    // 保存或更新分享访问口令
    @Transactional
    public String saveTicket(TicketCreator creator) {
        String ticket = creator.getTicket();
        validateTicket(ticket);
        if (StringUtils.isNotBlank(ticket)) {
            CoreShareTicket ticketEntity = getByTicket(ticket);
            if (ObjectUtils.isNotEmpty(ticketEntity)) {
                if (creator.isGenerateNew()) {
                    ticketEntity.setAccessTime(null);
                    ticketEntity.setTicket(CodingUtil.shortUuid());
                    coreShareTicketMapper.deleteById(ticketEntity);
                    coreShareTicketMapper.insert(ticketEntity);
                    return ticketEntity.getTicket();
                }
                ticketEntity.setArgs(creator.getArgs());
                ticketEntity.setExp(creator.getExp());
                ticketEntity.setUuid(creator.getUuid());
                coreShareTicketMapper.deleteById(ticketEntity);
                coreShareTicketMapper.insert(ticketEntity);
                return ticketEntity.getTicket();
            }
        }
        if (StringUtils.isBlank(ticket)) {
            ticket = CodingUtil.shortUuid();
        }
        CoreShareTicket linkTicket = new CoreShareTicket();
        linkTicket.setId(IDUtils.snowID());
        linkTicket.setTicket(ticket);
        linkTicket.setArgs(creator.getArgs());
        linkTicket.setExp(creator.getExp());
        linkTicket.setUuid(creator.getUuid());
        Objects.requireNonNull(CommonBeanFactory.proxy(this.getClass())).saveDao(linkTicket);
        return ticket;
    }

    // 校验分享访问口令格式
    private void validateTicket(String ticket) {
        if (StringUtils.isBlank(ticket)) {
            return;
        }
        if (ticket.length() < MIN_TICKET_LENGTH || ticket.length() > MAX_TICKET_LENGTH) {
            CrestException.throwException("分享链接标识长度不合法");
        }
        for (int i = 0; i < ticket.length(); i++) {
            char current = ticket.charAt(i);
            if (!Character.isLetterOrDigit(current) && current != '_' && current != '-') {
                CrestException.throwException("分享链接标识包含非法字符");
            }
        }
    }

    // 保存分享口令实体
    public void saveDao(CoreShareTicket ticket) {
        coreShareTicketMapper.insert(ticket);
    }

    // 删除指定分享口令
    public void deleteTicket(TicketDelRequest request) {
        String ticket = request.getTicket();
        if (StringUtils.isBlank(ticket)) {
            CrestException.throwException("ticket为必填参数");
        }
        QueryWrapper<CoreShareTicket> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ticket", ticket);
        coreShareTicketMapper.delete(queryWrapper);
    }

    // 切换分享是否需要口令
    public void switchRequire(TicketSwitchRequest request) {
        String resourceId = request.getResourceId();
        Boolean require = request.getRequire();
        QueryWrapper<CoreShare> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("resource_id", resourceId);
        queryWrapper.eq("creator", AuthUtils.getUser().getUserId());
        CoreShare coreShare = coreShareMapper.selectOne(queryWrapper);
        coreShare.setTicketRequire(require);
        coreShareMapper.updateById(coreShare);
    }

    // 分页查询资源分享口令
    public IPage<TicketVO> query(Long resourceId, Page<TicketVO> page) {
        QueryWrapper<CoreShare> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("resource_id", resourceId);
        queryWrapper.eq("creator", AuthUtils.getUser().getUserId());
        CoreShare coreShare = coreShareMapper.selectOne(queryWrapper);
        if (ObjectUtils.isEmpty(coreShare)) return null;
        String uuid = coreShare.getUuid();
        if (StringUtils.isBlank(uuid)) return null;
        QueryWrapper<CoreShareTicket> ticketQueryWrapper = new QueryWrapper<>();
        ticketQueryWrapper.eq("uuid", uuid);
        IPage<CoreShareTicket> pager = shareExtMapper.pager(page, ticketQueryWrapper);
        List<CoreShareTicket> records = pager.getRecords();
        IPage<TicketVO> iPage = new Page<>();
        iPage.setPages(pager.getPages());
        iPage.setTotal(pager.getTotal());
        iPage.setCurrent(pager.getCurrent());
        iPage.setSize(pager.getSize());
        List<TicketVO> vos = records.stream().map(record -> BeanUtils.copyBean(new TicketVO(), record)).toList();
        iPage.setRecords(vos);
        return iPage;
    }

    // 分享 UUID 变更后同步口令归属
    @Transactional
    public void updateByUuidChange(String originalUuid, String newUuid) {
        shareExtMapper.updateTicketUuid(originalUuid, newUuid);
    }

    // 删除指定分享下的全部口令
    @Transactional
    public void deleteByShare(String uuid) {
        QueryWrapper<CoreShareTicket> ticketQueryWrapper = new QueryWrapper<>();
        ticketQueryWrapper.eq("uuid", uuid);
        coreShareTicketMapper.delete(ticketQueryWrapper);
    }

    // 校验分享访问口令是否可用
    public TicketValidVO validateTicket(String ticket, CoreShare share) {
        TicketValidVO vo = new TicketValidVO();
        if (StringUtils.isBlank(ticket)) {
            vo.setTicketValid(!share.getTicketRequire());
            return vo;
        }
        CoreShareTicket linkTicket = getByTicket(ticket);
        if (ObjectUtils.isEmpty(linkTicket)) {
            vo.setTicketValid(false);
            return vo;
        }
        vo.setTicketValid(true);
        vo.setArgs(linkTicket.getArgs());
        Long accessTime = linkTicket.getAccessTime();
        long now = System.currentTimeMillis();
        if (ObjectUtils.isEmpty(accessTime)) {
            accessTime = now;
            vo.setTicketExp(false);
            linkTicket.setAccessTime(accessTime);
            coreShareTicketMapper.updateById(linkTicket);
            return vo;
        }
        Long exp = linkTicket.getExp();
        if (ObjectUtils.isEmpty(exp) || exp.equals(0L)) {
            vo.setTicketExp(false);
            return vo;
        }
        long expTime = exp * 60L * 1000L;
        long time = now - accessTime;
        vo.setTicketExp(time > expTime);
        return vo;
    }

    // 查询可创建口令数量上限
    public Integer getLimit() {
        return 0;
    }

    // 查询指定分享的口令数量
    public long ticketCount(String uuid) {
        QueryWrapper<CoreShareTicket> ticketQueryWrapper = new QueryWrapper<>();
        ticketQueryWrapper.eq("uuid", uuid);
        return coreShareTicketMapper.selectCount(ticketQueryWrapper);
    }
}
