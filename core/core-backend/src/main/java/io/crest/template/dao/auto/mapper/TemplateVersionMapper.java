package io.crest.template.dao.auto.mapper;

import io.crest.template.dao.auto.entity.TemplateVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内置模板初始化版本 Mapper。
 */
@Mapper
public interface TemplateVersionMapper extends BaseMapper<TemplateVersion> {

}
