package io.crest.api.free.vo;

import io.crest.api.free.dto.FreeRelationCategory;
import io.crest.api.free.dto.FreeRelationLink;
import io.crest.api.free.dto.FreeRelationNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
// 定义页面展示或接口返回的数据结构
public class FreeRelationVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 7087187548660162237L;

    private List<FreeRelationCategory> categories;

    private List<FreeRelationLink> links;

    private List<FreeRelationNode> nodes;

    private int maxNodeSize;
}
