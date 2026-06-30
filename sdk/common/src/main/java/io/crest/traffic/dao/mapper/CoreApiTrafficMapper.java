package io.crest.traffic.dao.mapper;

import io.crest.traffic.dao.entity.CoreApiTraffic;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CoreApiTrafficMapper {

    @Select("select `alive` from `core_api_traffic_limit` where `api` = #{api}")
    int getAlive(@Param("api") String api);

    @Update("update `core_api_traffic_limit` set alive = alive + 1 where `api` = #{api}")
    void upgrade(@Param("api") String api);

    @Insert("insert into core_api_traffic_limit values(#{id}, #{api}, #{threshold}, 0)")
    void insert(CoreApiTraffic traffic);

    @Select("select count(*) from core_api_traffic_limit where api = #{api}")
    Integer apiCount(@Param("api") String api);

    @Update("""
        update `core_api_traffic_limit` set alive =
        CASE WHEN alive > 0 THEN alive - 1
        ELSE alive END
        where `api` = #{api}
    """)
    void releaseAlive(@Param("api") String api);

    @Delete("delete from core_api_traffic_limit")
    void cleanTraffic();
}
