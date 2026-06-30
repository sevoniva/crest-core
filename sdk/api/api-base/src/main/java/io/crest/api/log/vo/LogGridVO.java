package io.crest.api.log.vo;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class LogGridVO implements Serializable {

    private String opText;

    private String opDetail;

    private String name;

    private String ip;

    private Long time;

    private boolean success;

    private String msg;
}
