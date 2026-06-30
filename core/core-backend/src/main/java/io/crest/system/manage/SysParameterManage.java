package io.crest.system.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.api.system.request.SQLBotConfigCreator;
import io.crest.api.system.vo.SettingItemVO;
import io.crest.api.system.vo.ShareBaseVO;
import io.crest.constant.SystemSettingConstants;
import io.crest.datasource.server.DatasourceServer;
import io.crest.system.dao.auto.entity.CoreSysSetting;
import io.crest.system.dao.auto.mapper.CoreSysSettingMapper;
import io.crest.system.dao.ext.mapper.ExtCoreSysSettingMapper;
import io.crest.utils.BeanUtils;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.IDUtils;
import io.crest.utils.SystemSettingUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("deprecation")
// 管理系统参数读取、保存和默认参数补齐
public class SysParameterManage {

    @Value("${crest.show-demo-tips:false}")
    private boolean showDemoTips;

    @Value("${crest.demo-tips-content:#{null}}")
    private String demoTipsContent;

    @Value("${crest.user.initial-password:}")
    private String configuredInitialPassword;

    @Resource
    private CoreSysSettingMapper coreSysSettingMapper;

    @Resource
    private ExtCoreSysSettingMapper extCoreSysSettingMapper;
    @Resource
    private DatasourceServer datasourceServer;

    // 按参数键查询单个参数值
    public String singleVal(String key) {
        QueryWrapper<CoreSysSetting> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pkey", key);
        CoreSysSetting sysSetting = coreSysSettingMapper.selectOne(queryWrapper);
        if (ObjectUtils.isNotEmpty(sysSetting)) {
            return sysSetting.getPval();
        }
        return null;
    }

    // 按参数组查询键值映射
    public Map<String, String> groupVal(String groupKey) {
        QueryWrapper<CoreSysSetting> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("pkey", groupKey);
        queryWrapper.orderByAsc("sort");
        List<CoreSysSetting> sysSettings = coreSysSettingMapper.selectList(queryWrapper);
        if (!CollectionUtils.isEmpty(sysSettings)) {
            return sysSettings.stream()
                    .collect(Collectors.toMap(CoreSysSetting::getPkey, item -> StringUtils.defaultString(item.getPval())));
        }
        return new HashMap<>();
    }

    // 按参数组查询参数列表
    public List<CoreSysSetting> groupList(String groupKey) {
        QueryWrapper<CoreSysSetting> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("pkey", groupKey);
        queryWrapper.orderByAsc("sort");
        List<CoreSysSetting> sysSettings = coreSysSettingMapper.selectList(queryWrapper);
        if (Strings.CS.equals(groupKey, "basic.")) {
            appendBasicDefaults(sysSettings);
        }
        return sysSettings;
    }

    // 为基础设置补齐缺失的默认参数
    private void appendBasicDefaults(List<CoreSysSetting> sysSettings) {
        if (sysSettings.stream().noneMatch(item -> SystemSettingConstants.INITIAL_PASSWORD.equals(item.getPkey()))) {
            sysSettings.add(buildCoreSetting(SystemSettingConstants.INITIAL_PASSWORD, configuredInitialPassword, "pwd", 15));
        } else if (StringUtils.isNotBlank(configuredInitialPassword)) {
            sysSettings.stream()
                    .filter(item -> SystemSettingConstants.INITIAL_PASSWORD.equals(item.getPkey()) && StringUtils.isBlank(item.getPval()))
                    .forEach(item -> item.setPval(configuredInitialPassword));
        }
        appendIfMissing(sysSettings, "basic.pwdStrategy", "true", "text", 16);
        appendIfMissing(sysSettings, SystemSettingConstants.DIP, "false", "text", 17);
        appendIfMissing(sysSettings, SystemSettingConstants.PVP, "0", "text", 18);
        appendIfMissing(sysSettings, SystemSettingConstants.LOGIN_LIMIT, "false", "text", 19);
        appendIfMissing(sysSettings, SystemSettingConstants.LOGIN_LIMIT_RATE, "5", "text", 20);
        appendIfMissing(sysSettings, SystemSettingConstants.LOGIN_LIMIT_TIME, "30", "text", 21);
        sysSettings.sort(Comparator.comparing(CoreSysSetting::getSort));
    }

    // 参数不存在时追加默认设置项
    private void appendIfMissing(List<CoreSysSetting> sysSettings, String pkey, String pval, String type, int sort) {
        if (sysSettings.stream().noneMatch(item -> pkey.equals(item.getPkey()))) {
            sysSettings.add(buildCoreSetting(pkey, pval, type, sort));
        }
    }

    // 构造系统参数实体
    private CoreSysSetting buildCoreSetting(String pkey, String pval, String type, int sort) {
        CoreSysSetting sysSetting = new CoreSysSetting();
        sysSetting.setId(IDUtils.snowID());
        sysSetting.setPkey(pkey);
        sysSetting.setPval(StringUtils.defaultString(pval));
        sysSetting.setType(type);
        sysSetting.setSort(sort);
        return sysSetting;
    }
    // 将系统参数实体转换为前端设置项
    public List<SettingItemVO> convert(List<CoreSysSetting> sysSettings) {
        return sysSettings.stream().sorted(Comparator.comparing(CoreSysSetting::getSort)).map(item -> BeanUtils.copyBean(new SettingItemVO(), item)).toList();
    }
    // 查询前端初始化所需的 UI 参数
    public List<Object> getUiList() {
        List<Object> result = new ArrayList<>();
        result.add(buildSettingItem("community", true));
        result.add(buildSettingItem("showDemoTips", showDemoTips));
        result.add(buildSettingItem("demoTipsContent", demoTipsContent));
        String siteTitle = singleVal("basic.siteTitle");
        result.add(buildSettingItem("siteTitle", StringUtils.defaultIfBlank(siteTitle, "Crest")));
        return result;
    }
    // 查询默认登录方式
    public Integer defaultLogin() {
        return 0;
    }

    // 构造 UI 参数项
    private Map<String, Object> buildSettingItem(String pkey, Object pval) {
        Map<String, Object> item = new HashMap<>();
        item.put("pkey", pkey);
        item.put("pval", pval);
        return item;
    }


    // 保存指定参数组
    @Transactional
    public void saveGroup(List<SettingItemVO> vos, String groupKey) {
        List<CoreSysSetting> sysSettings = vos.stream().filter(vo -> !SystemSettingUtils.restrictedSetting(vo.getPkey())).map(item -> {
            CoreSysSetting sysSetting = BeanUtils.copyBean(new CoreSysSetting(), item);
            sysSetting.setId(IDUtils.snowID());
            return sysSetting;
        }).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(sysSettings)) {
            QueryWrapper<CoreSysSetting> queryWrapper = new QueryWrapper<>();
            sysSettings.forEach(sysSetting -> {
                queryWrapper.clear();
                queryWrapper.eq("pkey", sysSetting.getPkey());
                coreSysSettingMapper.delete(queryWrapper);
            });
            extCoreSysSettingMapper.saveBatch(sysSettings);
        }
        datasourceServer.addJob(sysSettings);
    }

    // 保存 SQL Bot 配置
    public void saveSqlBotConfig(SQLBotConfigCreator configVO) {
        List<CoreSysSetting> configList = new ArrayList<>();
        String key = "sqlbot.";
        CoreSysSetting domainVo = new CoreSysSetting();
        domainVo.setPkey(key + "domain");
        domainVo.setPval(configVO.getDomain());
        domainVo.setType("text");
        domainVo.setSort(0);
        domainVo.setId(IDUtils.snowID());
        configList.add(domainVo);

        CoreSysSetting idVo = new CoreSysSetting();
        idVo.setPkey(key + "id");
        idVo.setPval(configVO.getId());
        idVo.setType("text");
        idVo.setSort(0);
        idVo.setId(IDUtils.snowID());
        configList.add(idVo);

        CoreSysSetting enabledVo = new CoreSysSetting();
        enabledVo.setPkey(key + "enabled");
        enabledVo.setPval(configVO.getEnabled().toString());
        enabledVo.setType("text");
        enabledVo.setSort(0);
        enabledVo.setId(IDUtils.snowID());
        configList.add(enabledVo);

        CoreSysSetting validVo = new CoreSysSetting();
        validVo.setPkey(key + "valid");
        validVo.setPval(configVO.getValid().toString());
        validVo.setType("text");
        validVo.setSort(0);
        validVo.setId(IDUtils.snowID());
        configList.add(validVo);


        QueryWrapper<CoreSysSetting> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("pkey", key);
        coreSysSettingMapper.delete(queryWrapper);

        extCoreSysSettingMapper.saveBatch(configList);
    }
    // 保存基础参数组
    @Transactional
    public void saveBasic(List<SettingItemVO> vos) {
        String key = "basic.";
        proxy().saveGroup(vos, key);
    }

    // 获取当前类代理以触发事务增强
    private SysParameterManage proxy() {
        return CommonBeanFactory.getBean(SysParameterManage.class);
    }

    // 查询分享基础设置
    public ShareBaseVO shareBase() {
        String disableText = singleVal("basic.shareDisable");
        String requireText = singleVal("basic.sharePeRequire");
        ShareBaseVO vo = new ShareBaseVO();
        if (StringUtils.isNotBlank(disableText) && Strings.CS.equals("true", disableText)) {
            vo.setDisable(true);
        }
        if (StringUtils.isNotBlank(requireText) && Strings.CS.equals("true", requireText)) {
            vo.setPeRequire(true);
        }
        return vo;
    }

    // 新增系统参数
    public void insert(CoreSysSetting coreSysSetting) {
        coreSysSettingMapper.insert(coreSysSetting);
    }

}
