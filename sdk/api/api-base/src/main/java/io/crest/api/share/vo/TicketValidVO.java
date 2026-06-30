package io.crest.api.share.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class TicketValidVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 2452043685969885580L;

    private boolean ticketValid;

    private boolean ticketExp;

    private String args;
}
