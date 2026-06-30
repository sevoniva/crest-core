package io.crest.template.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.api.template.TemplateManageApi;
import io.crest.api.template.dto.TemplateManageDTO;
import io.crest.api.template.request.TemplateManageBatchRequest;
import io.crest.api.template.request.TemplateManageRequest;
import io.crest.api.template.vo.VisualizationTemplateVO;
import io.crest.constant.CommonConstants;
import io.crest.exception.CrestException;
import io.crest.template.dao.auto.entity.VisualizationTemplate;
import io.crest.template.dao.auto.entity.VisualizationTemplateCategory;
import io.crest.template.dao.auto.entity.VisualizationTemplateCategoryRelation;
import io.crest.template.dao.auto.mapper.VisualizationTemplateCategoryRelationMapper;
import io.crest.template.dao.auto.mapper.VisualizationTemplateCategoryMapper;
import io.crest.template.dao.auto.mapper.VisualizationTemplateMapper;
import io.crest.template.dao.ext.ExtVisualizationTemplateMapper;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.visualization.server.StaticResourceServer;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static io.crest.constant.StaticResourceConstants.UPLOAD_URL_PREFIX;

/**
 * 管理可视化模板目录、模板内容和模板静态资源。
 */
@RestController
@RequestMapping("/template-manage")
@ConditionalOnProperty(prefix = "crest.internal-lite", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TemplateManageService implements TemplateManageApi {

    /**
     * 模板主表 Mapper
     */
    @Resource
    private VisualizationTemplateMapper templateMapper;

    /**
     * 模板分类 Mapper
     */
    @Resource
    private VisualizationTemplateCategoryMapper templateCategoryMapper;
    /**
     * 模板与分类关系 Mapper
     */
    @Resource
    private VisualizationTemplateCategoryRelationMapper categoryRelationMapper;
    /**
     * 模板扩展查询 Mapper
     */
    @Resource
    private ExtVisualizationTemplateMapper extTemplateMapper;
    /**
     * 静态资源服务，用于保存模板依赖资源
     */
    @Resource
    private StaticResourceServer staticResourceServer;

    /**
     * 查询模板树或模板列表
     */
    @Override
    public List<TemplateManageDTO> templateList(TemplateManageRequest request) {
        request.setWithBlobs("N");
        List<TemplateManageDTO> templateList = extTemplateMapper.findTemplateList(request);
        if (request.getWithChildren()) {
            getTreeChildren(templateList, request.getLeafDvType());
        }
        return templateList;
    }

    /**
     * 递归补全模板目录树子节点
     */
    public void getTreeChildren(List<TemplateManageDTO> parentTemplateList, String dvType) {
        Optional.ofNullable(parentTemplateList).ifPresent(parent -> parent.forEach(parentTemplate -> {
            List<TemplateManageDTO> panelTemplateDTOChildren = extTemplateMapper.findTemplateList(new TemplateManageRequest(parentTemplate.getId(), dvType));
            parentTemplate.setChildren(panelTemplateDTOChildren);
            getTreeChildren(panelTemplateDTOChildren, dvType);
        }));
    }

    /**
     * 查询系统模板类型列表
     */
    public List<TemplateManageDTO> getSystemTemplateType(TemplateManageRequest request) {
        return extTemplateMapper.findTemplateList(request);
    }


    /**
     * 保存模板分类或模板节点，并同步模板静态资源
     */
    @Transactional
    @Override
    public TemplateManageDTO save(TemplateManageRequest request) {
        if (StringUtils.isEmpty(request.getId())) {
            request.setId(UUID.randomUUID().toString());
            request.setCreateTime(System.currentTimeMillis());
            request.setCreateBy(AuthUtils.getUser().getUserId().toString());
            if ("template".equals(request.getNodeType()) || "app".equals(request.getNodeType())) {
                // 保存模板依赖的静态资源，保证导入后可直接预览。
                staticResourceServer.saveFilesToServe(request.getStaticResource());
                String snapshotName = request.getNodeType() + "-" + request.getId() + ".jpeg";
                staticResourceServer.saveSingleFileToServe(snapshotName, request.getSnapshot().replace("data:image/jpeg;base64,", ""));
                request.setSnapshot("/" + UPLOAD_URL_PREFIX + '/' + snapshotName);
            }
            //如果level 是0（第一级）指的是分类目录 设置父级为对应的templateType
            if (request.getLevel() == 0) {
                request.setPid(request.getTemplateType());
                String nameCheckResult = this.categoryNameCheck(CommonConstants.OPT_TYPE.INSERT, request.getName(), null);
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    CrestException.throwException("名称已存在");
                }
                VisualizationTemplateCategory templateCategory = new VisualizationTemplateCategory();
                BeanUtils.copyBean(templateCategory, request);
                templateCategoryMapper.insert(templateCategory);
            } else {//模板插入 同名的模板进行覆盖(先删除)
                // 删除旧的分类关系
                extTemplateMapper.deleteCategoryRelationByTemplate(request.getName(), null);
                // 模板删除
                QueryWrapper<VisualizationTemplate> wrapper = new QueryWrapper<>();
                wrapper.eq("name", request.getName());
                templateMapper.delete(wrapper);

                VisualizationTemplate template = new VisualizationTemplate();
                BeanUtils.copyBean(template, request);
                if (template.getVersion() == null) {
                    template.setVersion(2);
                }
                templateMapper.insert(template);
                // 插入分类关系
                request.getCategories().forEach(categoryId -> {
                    VisualizationTemplateCategoryRelation categoryRelation = new VisualizationTemplateCategoryRelation();
                    categoryRelation.setId(UUID.randomUUID().toString());
                    categoryRelation.setCategoryId(categoryId);
                    categoryRelation.setTemplateId(template.getId());
                    categoryRelationMapper.insert(categoryRelation);
                });

            }
        } else {
            if (request.getLevel() == 0) {
                String nameCheckResult = this.categoryNameCheck(CommonConstants.OPT_TYPE.UPDATE, request.getName(), request.getId());
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    CrestException.throwException("名称已存在");
                }
                VisualizationTemplateCategory templateCategory = new VisualizationTemplateCategory();
                BeanUtils.copyBean(templateCategory, request);
                templateCategoryMapper.updateById(templateCategory);
            } else {
                String nameCheckResult = this.nameCheck(CommonConstants.OPT_TYPE.UPDATE, request.getName(), request.getId());
                if (CommonConstants.CHECK_RESULT.EXIST_ALL.equals(nameCheckResult)) {
                    CrestException.throwException("名称已存在");
                }
                VisualizationTemplate template = new VisualizationTemplate();
                BeanUtils.copyBean(template, request);
                if (template.getVersion() == null) {
                    template.setVersion(2);
                }
                templateMapper.updateById(template);
                //更新分类
                // 删除旧的分类关系
                extTemplateMapper.deleteCategoryRelationByTemplate(null, request.getId());
                // 插入分类关系
                request.getCategories().forEach(categoryId -> {
                    VisualizationTemplateCategoryRelation categoryRelation = new VisualizationTemplateCategoryRelation();
                    categoryRelation.setId(UUID.randomUUID().toString());
                    categoryRelation.setCategoryId(categoryId);
                    categoryRelation.setTemplateId(request.getId());
                    categoryRelationMapper.insert(categoryRelation);
                });
            }

        }
        TemplateManageDTO templateManageDTO = new TemplateManageDTO();
        BeanUtils.copyBean(templateManageDTO, request);
        templateManageDTO.setLabel(request.getName());
        return templateManageDTO;
    }

    /**
     * 按操作类型校验模板名称是否重复
     */
    public String nameCheck(String optType, String name, String id) {
        QueryWrapper<VisualizationTemplate> wrapper = new QueryWrapper<>();
        if (CommonConstants.OPT_TYPE.INSERT.equals(optType)) {
            wrapper.eq("name", name);
        } else if (CommonConstants.OPT_TYPE.UPDATE.equals(optType)) {
            wrapper.eq("name", name);
            wrapper.ne("id", id);
        }
        List<VisualizationTemplate> templateList = templateMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(templateList)) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }

    /**
     * 校验指定分类下模板名称是否重复
     */
    @Override
    public String categoryTemplateNameCheck(TemplateManageRequest request) {
        Long result = extTemplateMapper.checkCategoryTemplateName(request.getName(), request.getCategories());
        if (result == 0) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }

    /**
     * 批量校验分类下模板名称是否重复
     */
    @Override
    public String checkCategoryTemplateBatchNames(TemplateManageRequest request) {
        Long result = extTemplateMapper.checkCategoryTemplateBatchNames(request.getTemplateNames(), request.getCategories(), request.getTemplateArray());
        if (result == 0) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }

    /**
     * 按操作类型校验分类名称是否重复
     */
    public String categoryNameCheck(String optType, String name, String id) {
        QueryWrapper<VisualizationTemplateCategory> wrapper = new QueryWrapper<>();
        if (CommonConstants.OPT_TYPE.INSERT.equals(optType)) {
            wrapper.eq("name", name);
        } else if (CommonConstants.OPT_TYPE.UPDATE.equals(optType)) {
            wrapper.eq("name", name);
            wrapper.ne("id", id);
        }
        List<VisualizationTemplateCategory> templateList = templateCategoryMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(templateList)) {
            return CommonConstants.CHECK_RESULT.NONE;
        } else {
            return CommonConstants.CHECK_RESULT.EXIST_ALL;
        }
    }

    /**
     * 按请求对象校验模板名称
     */
    @Override
    public String nameCheck(TemplateManageRequest request) {
        return nameCheck(request.getOptType(), request.getName(), request.getId());
    }

    /**
     * 从指定分类中删除模板引用，必要时删除模板本体
     */
    @Override
    public void delete(String id, String categoryId) {
        Assert.notNull(id, "id cannot be null");
        Assert.notNull(categoryId, "categoryId cannot be null");
        QueryWrapper<VisualizationTemplateCategoryRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("template_id", id);
        queryWrapper.eq("category_id", categoryId);
        categoryRelationMapper.delete(queryWrapper);
        // 如果是最后一个分类引用，则实际模板也需要删除
        Long result = extTemplateMapper.checkRepeatTemplateId(categoryId, id);
        if (result == 0) {
            templateMapper.deleteById(id);
        }
    }

    /**
     * 删除模板分类，存在共享模板时返回重复标识
     */
    @Override
    public String deleteCategory(String id) {
        Assert.notNull(id, "id cannot be null");
        // 该分类下是否有其他分类公用的模板

        Long checkResult = extTemplateMapper.checkCategoryRelation(id);
        if (checkResult == 0) {
            templateCategoryMapper.deleteById(id);
            QueryWrapper<VisualizationTemplateCategoryRelation> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("category_id", id);
            categoryRelationMapper.delete(queryWrapper);
            return "success";
        } else {
            return "repeat";
        }
    }

    /**
     * 查询单个模板详情及其分类列表
     */
    @Override
    public VisualizationTemplateVO findOne(String templateId) {
        VisualizationTemplate template = templateMapper.selectById(templateId);
        if (template != null) {
            VisualizationTemplateVO templateVO = new VisualizationTemplateVO();
            BeanUtils.copyBean(templateVO, template);
            //查找分类
            List<String> categories = extTemplateMapper.findTemplateCategories(templateId);
            templateVO.setCategories(categories);
            return templateVO;
        } else {
            return null;
        }
    }

    /**
     * 查询一组模板共同关联的分类
     */
    @Override
    public List<String> findCategoriesByTemplateIds(TemplateManageRequest request) throws Exception {
        if (!CollectionUtils.isEmpty(request.getTemplateArray())) {
            List<String> result = extTemplateMapper.findTemplateArrayCategories(request.getTemplateArray());
            if (!CollectionUtils.isEmpty(result) && result.size() == 1) {
                return Arrays.stream(result.get(0).split(",")).toList();
            }
        }
        return new ArrayList<>();
    }

    /**
     * 按条件查询模板列表
     */
    @Override
    public List<TemplateManageDTO> find(TemplateManageRequest request) {
        return extTemplateMapper.findTemplateList(request);
    }

    /**
     * 按条件查询模板分类
     */
    @Override
    public List<TemplateManageDTO> findCategories(TemplateManageRequest request) {
        return extTemplateMapper.findCategories(request);
    }

    /**
     * 批量更新模板所属分类
     */
    @Override
    public void batchUpdate(TemplateManageBatchRequest request) {
        request.getTemplateIds().forEach(templateId -> {
            // 删除旧的分类关系
            extTemplateMapper.deleteCategoryRelationByTemplate(null, templateId);
            // 插入分类关系
            request.getCategories().forEach(categoryId -> {
                VisualizationTemplateCategoryRelation categoryRelation = new VisualizationTemplateCategoryRelation();
                categoryRelation.setId(UUID.randomUUID().toString());
                categoryRelation.setCategoryId(categoryId);
                categoryRelation.setTemplateId(templateId);
                categoryRelationMapper.insert(categoryRelation);
            });
        });
    }

    /**
     * 批量从分类中删除模板引用
     */
    @Override
    public void batchDelete(TemplateManageBatchRequest request) {
        request.getTemplateIds().forEach(templateId -> {
            request.getCategories().forEach(categoryId -> {
                QueryWrapper<VisualizationTemplateCategoryRelation> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("template_id", templateId);
                queryWrapper.eq("category_id", categoryId);
                categoryRelationMapper.delete(queryWrapper);
                // 当前分类是该模板的最后一个引用时，删除模板记录。
                Long result = extTemplateMapper.checkRepeatTemplateId(categoryId, templateId);
                if (result == 0) {
                    templateMapper.deleteById(templateId);
                }
            });

        });
    }
}
