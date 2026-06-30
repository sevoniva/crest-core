package io.crest.api.ds.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义页面展示或接口返回的数据结构
public class TreeNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -3290175350481291605L;

    private Long id;

    private String name;

    private String type;

    private Boolean valid;
}
