package io.crest.api.permissions.org.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义页面展示或接口返回的数据结构
public class OrgDetailVO {

    private Long id;

    private String name;

    private Long pid;

    private String rootPath;

}
