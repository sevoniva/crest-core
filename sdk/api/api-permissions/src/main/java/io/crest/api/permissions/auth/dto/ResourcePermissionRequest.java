package io.crest.api.permissions.auth.dto;

import io.crest.api.permissions.user.vo.UserReciVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ResourcePermissionRequest implements Serializable {

    private List<Long> resourceIds;

    private Integer resourceType;

    private List<UserReciVO> targets;
}
