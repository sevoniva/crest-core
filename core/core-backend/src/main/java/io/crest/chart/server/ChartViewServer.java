package io.crest.chart.server;

import io.crest.api.chart.ChartViewApi;
import io.crest.api.chart.vo.ChartBaseVO;
import io.crest.api.chart.vo.ViewSelectorVO;
import io.crest.chart.manage.ChartViewManege;
import io.crest.constant.CommonConstants;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.exception.CrestException;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.ChartViewFieldDTO;
import io.crest.result.ResultCode;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("chart")
/**
 * 图表视图接口控制器，负责转发图表数据、字段和基础信息请求
 */
public class ChartViewServer implements ChartViewApi {
    @Resource
    private ChartViewManege chartViewManege;

    /**
     * 获取指定图表在核心资源表中的完整数据
     */
    @Override
    public ChartViewDTO getData(Long id) throws Exception {
        try {
            return chartViewManege.getChart(id, CommonConstants.RESOURCE_TABLE.CORE);
        } catch (Exception e) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), e.getMessage());
        }
        return null;
    }

    /**
     * 获取图表可用的维度和指标字段，并对字段信息做输出编码
     */
    @Override
    public Map<String, List<ChartViewFieldDTO>> listByDQ(Long id, Long chartId, ChartViewDTO dto) {
        Map<String, List<ChartViewFieldDTO>> stringListMap = chartViewManege.listByDQ(id, chartId, dto);
        DatasetUtils.listEncode(stringListMap.get("dimensionList"));
        DatasetUtils.listEncode(stringListMap.get("quotaList"));
        return stringListMap;
    }

    /**
     * 保存图表视图配置
     */
    @Override
    public ChartViewDTO save(ChartViewDTO dto) throws Exception {
        return chartViewManege.save(dto);
    }

    /**
     * 校验两个视图是否使用同一数据集
     */
    @Override
    public String checkSameDataSet(String viewIdSource, String viewIdTarget) {
        return chartViewManege.checkSameDataSet(viewIdSource, viewIdTarget);
    }

    /**
     * 获取指定资源表中的图表详情
     */
    @Override
    public ChartViewDTO getDetail(Long id, String resourceTable) {
        return chartViewManege.getDetails(id, resourceTable);
    }

    /**
     * 获取资源下可供选择的图表视图
     */
    @Override
    public List<ViewSelectorVO> viewOption(Long resourceId) {
        return chartViewManege.viewOption(resourceId);
    }

    /**
     * 复制图表字段配置
     */
    @Override
    public void copyField(Long id, Long chartId) {
        chartViewManege.copyField(id, chartId);
    }

    /**
     * 移除视图字段引用
     */
    @Override
    public void fieldRemoval(Long id) {
        chartViewManege.fieldRemoval(id);
    }

    /**
     * 按图表移除字段引用
     */
    @Override
    public void chartFieldRemoval(Long chartId) {
        chartViewManege.chartFieldRemovalId(chartId);
    }

    /**
     * 获取图表基础信息
     */
    @Override
    public ChartBaseVO chartBaseInfo(Long id, String resourceTable) {
        return chartViewManege.chartBaseInfo(id, resourceTable);
    }
}
