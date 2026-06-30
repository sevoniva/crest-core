package io.crest.visualization.server;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.api.visualization.VisualizationBackgroundApi;
import io.crest.api.visualization.vo.VisualizationBackgroundVO;
import io.crest.i18n.Translator;
import io.crest.utils.BeanUtils;
import io.crest.visualization.dao.auto.entity.VisualizationBackground;
import io.crest.visualization.dao.auto.mapper.VisualizationBackgroundMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询可视化背景资源，并按分类返回给编辑器使用。
 */
@RestController
@RequestMapping("/visualization-background")
@ConditionalOnProperty(prefix = "crest.feature.visualization-background", name = "enabled", havingValue = "true")
public class VisualizationBackgroundService implements VisualizationBackgroundApi {
    @Resource
    VisualizationBackgroundMapper mapper;

    @Override
    public Map<String, List<VisualizationBackgroundVO>> findAll() {
        List<VisualizationBackground> result = mapper.selectList(new QueryWrapper<>());
        return result.stream().map(vb ->{
            VisualizationBackgroundVO vbVO = new VisualizationBackgroundVO();
            BeanUtils.copyBean(vbVO,vb);
            vbVO.setName(Translator.get("i18n_board")+vbVO.getName());
            return vbVO;
        }).collect(Collectors.groupingBy(VisualizationBackgroundVO::getClassification));
    }
}
