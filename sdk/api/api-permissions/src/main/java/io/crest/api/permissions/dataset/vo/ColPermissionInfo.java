package io.crest.api.permissions.dataset.vo;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class ColPermissionInfo implements Serializable {

    private boolean selected;

    private Long id;

    private String opt;

    private Object desensitizationRule;

}
