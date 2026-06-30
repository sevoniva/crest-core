package io.crest.visualization.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.visualization.VisualizationStoreApi;
import io.crest.api.visualization.request.VisualizationStoreRequest;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.visualization.vo.VisualizationStoreVO;
import io.crest.visualization.manage.VisualizationStoreManage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/store")
@RestController
// 实现接口服务，衔接业务处理和返回结果
public class VisualizationStoreServer implements VisualizationStoreApi {

    @Resource
    private VisualizationStoreManage visualizationStoreManage;

    @Override
    // 执行当前业务请求并写入处理结果
    public void execute(VisualizationStoreRequest request) {
        visualizationStoreManage.execute(request);
    }

    @Override
    // 查询当前业务数据列表
    public List<VisualizationStoreVO> query(VisualizationWorkbranchQueryRequest request) {
        IPage<VisualizationStoreVO> iPage = visualizationStoreManage.query(1, 20, request);
        return iPage.getRecords();
    }

    @Override
    public boolean favorited(Long id) {
        return visualizationStoreManage.favorited(id);
    }
}
