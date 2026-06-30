package io.crest.extensions.view.dto;

import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * 表格表头配置，包含分组表头和辅助表头信息
 */
public class TableHeader {
    /**
     * 分组表头配置
     */
    private HeaderGroupConfig headerGroupConfig;
    /**
     * 是否启用分组表头
     */
    private boolean headerGroup;
    /**
     * 辅助表头配置
     */
    private AuxiliaryHeader auxiliaryHeader;


    @Data
    static
    /**
     * 分组表头的元数据和列树配置
     */
    public class HeaderGroupConfig {
        /**
         * 分组表头元信息列表
         */
        private List<MetaInfo> meta = new ArrayList<>();
        /**
         * 分组表头列树
         */
        private List<ColumnInfo> columns = new ArrayList<>();
    }

    @Data
    static
    /**
     * 分组表头列节点
     */
    public class ColumnInfo {
        /**
         * 列节点唯一键
         */
        @Getter
        private String key;
        /**
         * 子列节点列表
         */
        private List<ColumnInfo> children = new ArrayList<>();
        /**
         * 列宽
         */
        private Integer width;
    }

    @Getter
    @Data
    static
    /**
     * 表头元信息
     */
    public class MetaInfo {
        /**
         * 字段名
         */
        private String field;
        /**
         * 展示名称
         */
        private String name;

    }

    @Data
    static
    /**
     * 辅助表头配置
     */
    public class AuxiliaryHeader {
        /**
         * 是否启用辅助表头
         */
        private boolean enabled;
        /**
         * 辅助表头行高
         */
        private Integer rowHeight;
        /**
         * 背景颜色
         */
        private String backgroundColor;
        /**
         * 字体颜色
         */
        private String fontColor;
        /**
         * 字体大小
         */
        private Integer fontSize;
        /**
         * 文本对齐方式
         */
        private String align;
        /**
         * 辅助说明列表
         */
        private List<DescriptionInfo> descriptions = new ArrayList<>();
    }

    @Data
    static
    /**
     * 辅助表头说明项
     */
    public class DescriptionInfo {
        /**
         * 关联字段
         */
        private String field;
        /**
         * 说明文本
         */
        private String text;
    }
}
