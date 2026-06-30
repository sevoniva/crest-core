package io.crest.dataset.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.api.dataset.dto.SqlLogDTO;
import io.crest.dataset.dao.auto.entity.CoreDatasetTableSqlLog;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableSqlLogMapper;
import io.crest.utils.BeanUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Transactional(rollbackFor = Exception.class)
// 封装当前业务的持久化和查询逻辑
public class DatasetTableSqlLogManage {
    @Resource
    private CoreDatasetTableSqlLogMapper coreDatasetTableSqlLogMapper;

    // 保存当前业务数据
    public void save(SqlLogDTO dto) {
        if (dto == null) {
            return;
        }
        CoreDatasetTableSqlLog coreDatasetTableSqlLog = new CoreDatasetTableSqlLog();
        BeanUtils.copyBean(coreDatasetTableSqlLog, dto);
        if (ObjectUtils.isEmpty(coreDatasetTableSqlLog.getId())) {
            coreDatasetTableSqlLog.setId(UUID.randomUUID().toString());
            coreDatasetTableSqlLogMapper.insert(coreDatasetTableSqlLog);
        } else {
            coreDatasetTableSqlLogMapper.updateById(coreDatasetTableSqlLog);
        }
    }

    // 查询当前业务数据列表
    public List<SqlLogDTO> listByTableId(SqlLogDTO dto) {
        if (dto == null || ObjectUtils.isEmpty(dto.getTableId())) {
            return null;
        }
        QueryWrapper<CoreDatasetTableSqlLog> wrapper = new QueryWrapper<>();
        wrapper.eq("table_id", dto.getTableId());
        List<CoreDatasetTableSqlLog> coreDatasetTableSqlLogs = coreDatasetTableSqlLogMapper.selectList(wrapper);
        return coreDatasetTableSqlLogs.stream().map(ele -> {
            SqlLogDTO s = new SqlLogDTO();
            BeanUtils.copyBean(s, ele);
            return s;
        }).collect(Collectors.toList());
    }

    public void tableRemoval(String id) {
        QueryWrapper<CoreDatasetTableSqlLog> wrapper = new QueryWrapper<>();
        wrapper.eq("table_id", id);
        coreDatasetTableSqlLogMapper.delete(wrapper);
    }
}
