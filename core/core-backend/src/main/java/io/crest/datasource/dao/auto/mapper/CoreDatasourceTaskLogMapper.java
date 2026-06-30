package io.crest.datasource.dao.auto.mapper;

import io.crest.datasource.dao.auto.entity.CoreDatasourceTaskLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源同步任务日志 Mapper。
 */
@Mapper
public interface CoreDatasourceTaskLogMapper extends BaseMapper<CoreDatasourceTaskLog> {

}
