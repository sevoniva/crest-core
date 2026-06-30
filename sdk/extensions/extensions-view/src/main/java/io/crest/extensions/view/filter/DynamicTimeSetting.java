package io.crest.extensions.view.filter;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义过滤条件的数据结构和匹配信息
public class DynamicTimeSetting implements Serializable {
    private String relativeToCurrent;//相对当前 thisYear ｜ lastYear ｜ thisMonth ｜ lastMonth ｜ today ｜ yesterday ｜ monthBeginning ｜ yearBeginning
    private String timeGranularity;//时间粒度 year ｜ month ｜ date ｜ datetime
    private Integer timeNum;// 数值
    private String relativeToCurrentType;// year ｜ month ｜ date
    private String around;// 前 f ｜ 后 b
    private String arbitraryTime;//timeGranularity = datetime时 取时分秒
}
