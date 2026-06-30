package io.crest.api.permissions.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
// 定义页面展示或接口返回的数据结构
public class PermissionValVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -6677497144947905694L;

    private int weight;

    private int ext;
}
