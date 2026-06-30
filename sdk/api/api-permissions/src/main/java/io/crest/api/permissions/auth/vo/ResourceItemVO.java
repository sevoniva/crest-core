package io.crest.api.permissions.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
// 定义页面展示或接口返回的数据结构
public class ResourceItemVO extends ResourceNodeVO {
    private String account;
}
