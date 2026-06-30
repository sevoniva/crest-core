package io.crest.extensions.view.filter;

import lombok.Data;

import java.util.List;


@Data
// 定义过滤条件的数据结构和匹配信息
public class FilterTreeObj {
    private String logic;
    private List<FilterTreeItem> items;
}
