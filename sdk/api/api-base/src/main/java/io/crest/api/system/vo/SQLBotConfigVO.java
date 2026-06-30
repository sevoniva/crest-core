package io.crest.api.system.vo;

import io.crest.api.system.request.SQLBotConfigCreator;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义页面展示或接口返回的数据结构
public class SQLBotConfigVO extends SQLBotConfigCreator implements Serializable {
}
