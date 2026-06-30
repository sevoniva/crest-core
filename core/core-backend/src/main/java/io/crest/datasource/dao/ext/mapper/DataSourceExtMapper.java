package io.crest.datasource.dao.ext.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.crest.datasource.dao.ext.po.Ctimestamp;
import io.crest.datasource.dao.ext.po.DataSourceNodePO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DataSourceExtMapper extends BaseMapper<DataSourceNodePO> {


    @Select("""
            SELECT FLOOR((CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS DATE) - DATE '1970-01-01') * 86400) AS currentTimestamp
            FROM DUAL
            """)
    @Results(
            id = "selectTimestamp",
            value = {
                    @Result(property = "currentTimestamp", column = "currentTimestamp")
            }
    )
    Ctimestamp selectTimestamp();

}
