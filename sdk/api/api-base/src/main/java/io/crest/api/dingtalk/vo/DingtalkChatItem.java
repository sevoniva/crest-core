package io.crest.api.dingtalk.vo;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class DingtalkChatItem implements Serializable {

    private String id;

    private String name;
}
