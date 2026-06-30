package io.crest.visualization.dao.ext.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.visualization.dao.ext.po.VisualizationNodePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Set;

@Mapper
public interface CoreVisualiationExtMapper {

    @Select("""
            select id, name, pid, node_type, type, create_by, org_id, mobile_layout as extraFlag, status as extraFlag1 from core_visualization
            ${ew.customSqlSegment}
            """)
    List<VisualizationNodePO> queryNodes(@Param("ew") QueryWrapper<Object> queryWrapper);

    @Select("select id from core_visualization where pid = #{pid} and delete_flag = 0")
    List<Long> queryChildrenId(@Param("pid") Long pid);

    @Update("""
            <script>
            update core_visualization set delete_flag = 1, delete_time = #{time}, delete_by = #{uid} where id in
            <foreach item='id' index='index' collection='ids' open='(' separator=',' close=')'>
            #{id}
            </foreach>
            </script>
            """)
    void batchDel(@Param("ids") Set<Long> ids, @Param("time") Long time, @Param("uid") Long uid);
}
