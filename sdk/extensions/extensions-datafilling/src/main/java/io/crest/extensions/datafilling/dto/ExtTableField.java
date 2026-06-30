package io.crest.extensions.datafilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
/**
 * 数据填报扩展表字段定义
 */
public class ExtTableField implements Serializable {
    @Serial
    private static final long serialVersionUID = 9021129395822053871L;

    /**
     * 字段组件类型
     */
    private String type;

    /**
     * 字段组件类型名称
     */
    private String typeName;

    /**
     * 字段组件图标
     */
    private String icon;

    /**
     * 字段唯一标识
     */
    private String id;

    /**
     * 字段详细配置
     */
    private ExtTableFieldSetting settings;

    /**
     * 字段是否已被移除
     */
    private boolean removed;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    /**
     * 数据填报字段渲染和校验设置
     */
    public static class ExtTableFieldSetting implements Serializable  {

        @Serial
        private static final long serialVersionUID = 8776508642526681125L;

        /**
         * 字段展示名称
         */
        private String name;

        /**
         * 字段是否必填
         */
        private boolean required;

        /**
         * 字段与数据库列的映射关系
         */
        private ExtTableFieldMapping mapping;

        /**
         * 范围值分隔符
         */
        private String rangeSeparator;

        /**
         * 字段值是否要求唯一
         */
        private boolean unique;

        /**
         * 输入控件类型
         */
        private String inputType;

        /**
         * 日期控件类型
         */
        private String dateType;

        /**
         * 输入占位文案
         */
        private String placeholder;
        /**
         * 范围开始占位文案
         */
        private String startPlaceholder;
        /**
         * 范围结束占位文案
         */
        private String endPlaceholder;

        /**
         * 选项来源类型
         */
        private Integer optionSourceType;

        /**
         * 选项来源数据源 ID
         */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long optionDatasource;
        /**
         * 选项来源表名
         */
        private String optionTable;
        /**
         * 选项来源字段名
         */
        private String optionColumn;
        /**
         * 选项排序字段
         */
        private String optionOrder;

        /**
         * 是否允许多选
         */
        private boolean multiple;

        /**
         * 是否启用更新规则校验
         */
        private boolean updateRuleCheck;

        /**
         * 静态选项列表
         */
        private List<Option> options;

        /**
         * 额外列配置
         */
        private List<ExtraColumnItem> extraColumns;

        /**
         * 是否启用默认时间
         */
        private boolean enableDefaultTime;
        /**
         * 是否使用当前时间作为默认值
         */
        private boolean enableCurrentTime;
        /**
         * 默认时间值
         */
        private Long defaultTime;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    /**
     * 字段静态选项
     */
    public static class Option implements Serializable  {

        @Serial
        private static final long serialVersionUID = -1681618296840344071L;

        /**
         * 选项名称
         */
        private String name;

        /**
         * 选项值
         */
        private Object value;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    /**
     * 字段与数据库列的映射配置
     */
    public static class ExtTableFieldMapping implements Serializable  {

        @Serial
        private static final long serialVersionUID = 4233066732126872840L;

        /**
         * 主字段列名
         */
        private String columnName;

        // 日期范围控件对应的两个列名
        private String columnName1;
        private String columnName2;

        /**
         * 变更前的主字段列名
         */
        private String oldColumnName;
        /**
         * 变更前的日期范围开始列名
         */
        private String oldColumnName1;
        /**
         * 变更前的日期范围结束列名
         */
        private String oldColumnName2;

        /**
         * 字段基础类型
         */
        private BaseType type;

        // 字段长度
        private Integer size;
        // 数值精度
        private Integer accuracy;

        /**
         * 是否复用已存在的数据表
         */
        private boolean useExistsTable;

    }

    /**
     * 数据填报字段支持的基础类型
     */
    public enum BaseType {
        nvarchar, //字符串
        text, //长文本
        number, //整型数字
        decimal, //小数数字
        datetime //日期
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    /**
     * 数据库表字段定义
     */
    public static class TableField implements Serializable  {

        @Serial
        private static final long serialVersionUID = 85092190247927362L;

        /**
         * 字段列名
         */
        private String columnName;

        /**
         * 原字段列名
         */
        private String oldColumnName;

        /**
         * 字段基础类型
         */
        private BaseType type;

        /**
         * 字段是否必填
         */
        private boolean required;

        /**
         * 字段是否为主键
         */
        private boolean primaryKey;

        // 字段长度
        private Integer size;
        // 数值精度
        private Integer accuracy;

        /**
         * 字段备注
         */
        private String comment;

    }

}
