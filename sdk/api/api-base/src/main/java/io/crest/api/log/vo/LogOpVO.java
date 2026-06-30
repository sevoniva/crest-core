package io.crest.api.log.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义页面展示或接口返回的数据结构
public class LogOpVO implements Serializable {

    private String value;

    private String label;

    private List<LogOpVO> children;
}
