package io.crest.visualization.dao.ext.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.visualization.dao.ext.po.StorePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CoreStoreExtMapper {

    @Select("""
            select
            s.id as store_id,
            v.id as resource_id,
            v.type,
            v.create_by as creator,
            v.update_by as editor,
            v.update_time as edit_time,
            v.name,
            v.mobile_layout as ext_flag,
            v.status as ext_flag1
            from core_workspace_favorite_resource s
            inner join core_visualization v on s.resource_id = v.id
            ${ew.customSqlSegment}
            """)
    IPage<StorePO> query(IPage<StorePO> page, @Param("ew") QueryWrapper<Object> ew);
}
