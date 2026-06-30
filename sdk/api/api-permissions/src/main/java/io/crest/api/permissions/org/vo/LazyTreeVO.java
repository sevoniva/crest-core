package io.crest.api.permissions.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
@Schema(description = "组织树VO")
@Data
// 定义页面展示或接口返回的数据结构
public class LazyTreeVO implements Serializable {
    @Schema(description = "节点")
    private List<LazyOrgTreeNode> nodes;
    @Schema(description = "展开节点")
    private List<String> expandKeyList;
}
