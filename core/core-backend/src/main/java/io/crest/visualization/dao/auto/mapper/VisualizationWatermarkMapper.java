package io.crest.visualization.dao.auto.mapper;

import io.crest.visualization.dao.auto.entity.VisualizationWatermark;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 可视化水印配置 Mapper。
 */
@Mapper
public interface VisualizationWatermarkMapper extends BaseMapper<VisualizationWatermark> {

    @Update("""
    UPDATE `core_visualization_watermark` set `setting_content` = REPLACE(`setting_content`,'"enable":true','"enable":false')
    """)
    void disable();
}
