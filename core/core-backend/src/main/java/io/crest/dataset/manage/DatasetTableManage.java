package io.crest.dataset.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.dataset.dao.auto.entity.CoreDatasetTable;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableMapper;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.i18n.Translator;
import io.crest.utils.BeanUtils;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 管理数据集物理表元数据的保存、查询和清理
 */
@Component
public class DatasetTableManage {
    @Resource
    private CoreDatasetTableMapper coreDatasetTableMapper;

    /**
     * 保存或更新数据集表实体
     */
    public void save(CoreDatasetTable coreDatasetTable) {
        checkNameLength(coreDatasetTable.getName());
        checkNameLength(coreDatasetTable.getTableName());
        if (ObjectUtils.isEmpty(coreDatasetTable.getId())) {
            coreDatasetTable.setId(IDUtils.snowID());
            coreDatasetTableMapper.insert(coreDatasetTable);
        } else {
            coreDatasetTableMapper.updateById(coreDatasetTable);
        }
    }

    /**
     * 根据数据集表 DTO 保存或更新数据集表实体
     */
    public void save(DatasetTableDTO currentDs) {
        checkNameLength(currentDs.getName());
        checkNameLength(currentDs.getTableName());
        CoreDatasetTable coreDatasetTable = coreDatasetTableMapper.selectById(currentDs.getId());
        CoreDatasetTable record = new CoreDatasetTable();
        BeanUtils.copyBean(record, currentDs);
        if (ObjectUtils.isEmpty(coreDatasetTable)) {
            coreDatasetTableMapper.insert(record);
        } else {
            coreDatasetTableMapper.updateById(record);
        }
    }

    /**
     * 查询指定数据集分组下的全部表
     */
    public List<CoreDatasetTable> selectByDatasetGroupId(Long datasetGroupId) {
        QueryWrapper<CoreDatasetTable> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", datasetGroupId);
        return coreDatasetTableMapper.selectList(wrapper);
    }

    /**
     * 根据主键查询数据集表
     */
    public CoreDatasetTable selectById(Long id) {
        return coreDatasetTableMapper.selectById(id);
    }

    /**
     * 删除数据集分组中不再保留的表记录
     */
    public void deleteByDatasetGroupUpdate(Long datasetGroupId, List<Long> ids) {
        if (!CollectionUtils.isEmpty(ids)) {
            QueryWrapper<CoreDatasetTable> wrapper = new QueryWrapper<>();
            wrapper.eq("dataset_group_id", datasetGroupId);
            wrapper.notIn("id", ids);
            coreDatasetTableMapper.delete(wrapper);
        }
    }

    /**
     * 删除指定数据集分组下的全部表记录
     */
    public void deleteByDatasetGroupDelete(Long datasetGroupId) {
        QueryWrapper<CoreDatasetTable> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", datasetGroupId);
        coreDatasetTableMapper.delete(wrapper);
    }

    /**
     * 校验数据集表名称长度
     */
    private void checkNameLength(String name) {
        if (name != null && name.length() > 100) {
            CrestException.throwException(Translator.get("i18n_name_limit_100"));
        }
    }
}
