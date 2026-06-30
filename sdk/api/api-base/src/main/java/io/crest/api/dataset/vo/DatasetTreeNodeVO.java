package io.crest.api.dataset.vo;

import io.crest.api.dataset.dto.DatasetNodeDTO;
import io.crest.model.ITreeBase;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义页面展示或接口返回的数据结构
public class DatasetTreeNodeVO extends DatasetNodeDTO implements Serializable, ITreeBase<DatasetTreeNodeVO> {

    private List<DatasetTreeNodeVO> children;

    private Boolean leaf;

}
