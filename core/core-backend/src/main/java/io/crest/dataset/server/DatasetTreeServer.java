package io.crest.dataset.server;


import io.crest.api.dataset.DatasetTreeApi;
import io.crest.api.dataset.dto.DataSetExportRequest;
import io.crest.api.dataset.dto.DatasetNodeDTO;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.dataset.vo.DataSetBarVO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.dataset.manage.DatasetGroupManage;
import io.crest.exportCenter.manage.ExportCenterDownLoadManage;
import io.crest.exportCenter.manage.ExportCenterManage;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.view.dto.*;
import io.crest.log.CrestAudit;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.*;


@RestController
@RequestMapping("dataset-tree")
// 提供数据集目录和数据集导出相关接口
public class DatasetTreeServer implements DatasetTreeApi {
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private ExportCenterManage exportCenterManage;
    @Resource
    private ExportCenterDownLoadManage exportCenterDownLoadManage;


    // 保存数据集配置
    @CrestAudit(id = "#p0.id", ot = LogOT.MODIFY, st = LogST.DATASET)
    @Override
    public DatasetGroupInfoDTO save(DatasetGroupInfoDTO datasetNodeDTO) throws Exception {
        return datasetGroupManage.save(datasetNodeDTO, false, true);
    }

    // 重命名数据集节点
    @CrestAudit(id = "#p0.id", ot = LogOT.MODIFY, st = LogST.DATASET)
    @Override
    public DatasetNodeDTO rename(DatasetGroupInfoDTO dto) throws Exception {
        return datasetGroupManage.save(dto, true, false);
    }

    // 创建数据集节点
    @CrestAudit(id = "#p0.id", pid = "#p0.pid", ot = LogOT.CREATE, st = LogST.DATASET)
    @Override
    public DatasetNodeDTO create(DatasetGroupInfoDTO dto) throws Exception {
        return datasetGroupManage.save(dto, false, true);
    }

    // 移动数据集节点
    @CrestAudit(id = "#p0.id", ot = LogOT.MODIFY, st = LogST.DATASET)
    @Override
    public DatasetNodeDTO move(DatasetGroupInfoDTO dto) throws Exception {
        return datasetGroupManage.move(dto);
    }

    // 判断数据集节点是否允许删除
    @Override
    public boolean perDelete(Long id) {
        return datasetGroupManage.perDelete(id);
    }

    // 删除数据集节点
    @CrestAudit(id = "#p0", ot = LogOT.DELETE, st = LogST.DATASET)
    @Override
    public void delete(Long id) {
        datasetGroupManage.delete(id);
    }


    // 查询数据集业务树
    @CrestAudit(ot = LogOT.READ, st = LogST.DATASET)
    @Override
    public List<BusiNodeVO> tree(BusiNodeRequest request) {
        return datasetGroupManage.tree(request);
    }

    // 查询数据集侧边栏统计信息
    @Override
    public DataSetBarVO barInfo(Long id) {
        return datasetGroupManage.queryBarInfo(id);
    }

    // 查询数据集预览信息
    @Override
    public DatasetGroupInfoDTO get(Long id) throws Exception {
        return datasetGroupManage.datasetGroupInfoDTO(id, "preview");
    }

    // 查询数据集详情
    @Override
    public DatasetGroupInfoDTO details(Long id) throws Exception {
        return datasetGroupManage.getDetail(id);
    }

    // 批量查询仪表板引用的数据集详情
    @Override
    public List<DatasetTableDTO> panelGetDsDetails(List<Long> ids) throws Exception {
        return datasetGroupManage.getDetail(ids);
    }

    // 查询数据集 SQL 参数
    @Override
    public List<SqlVariableDetails> sqlParams(List<Long> ids) throws Exception {
        return datasetGroupManage.sqlParams(ids);
    }

    // 批量查询带权限的数据集详情
    @Override
    public List<DatasetTableDTO> detailWithPerm(List<Long> ids) throws Exception {
        return datasetGroupManage.getDetailWithPerm(ids);
    }

    // 导出数据集或创建导出任务
    @Override
    public void exportDataset(DataSetExportRequest request, HttpServletResponse response) throws Exception {
        if (request.isEmbeddedExport()) {
            exportCenterDownLoadManage.downloadDataset(request, response);
        } else {
            exportCenterManage.addTask(request.getId(), "dataset", request);
        }
    }

}
