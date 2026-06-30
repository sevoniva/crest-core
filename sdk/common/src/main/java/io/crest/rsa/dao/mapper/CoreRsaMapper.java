package io.crest.rsa.dao.mapper;

import io.crest.rsa.dao.entity.CoreRsa;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统加密密钥 Mapper。
 */
@Mapper
public interface CoreRsaMapper extends BaseMapper<CoreRsa> {

}
