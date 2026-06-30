package io.crest.template.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化模板与分类关系实体。
 */
@TableName("core_template_category_relation")
public class VisualizationTemplateCategoryRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 模板分类编号
     */
    private String categoryId;

    /**
     * 可视化模板编号
     */
    private String templateId;

    /**
     * 获取模板分类关系主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置模板分类关系主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取模板分类编号
     */
    public String getCategoryId() {
        return categoryId;
    }

    /**
     * 设置模板分类编号
     */
    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * 获取可视化模板编号
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 设置可视化模板编号
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * 返回模板分类关系的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationTemplateCategoryRelation{" +
        "id = " + id +
        ", categoryId = " + categoryId +
        ", templateId = " + templateId +
        "}";
    }
}
