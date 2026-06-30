package io.crest.api.dataset.engine;

/**
 * SQL 函数元数据枚举，用于描述函数展示名称、表达式和适用字段类型
 */
public enum SQLFunctionsEnum {
    SUBSTRING("SUBSTRING", "SUBSTRING(s,n,len)", 0, "获取从字符串s中的第n个位置开始长度为len的字符串", false),
    ABS("ABS", "ABS(x)", 2, "返回x的绝对值", false),
    CEIL("CEIL", "CEIL(x)", 2, "返回不小于x的最小整数", false),
    FLOOR("FLOOR", "FLOOR(x)", 2, "返回不大于x的最大整数", false),
    ROUND1("ROUND", "ROUND(x)", 2, "返回离x最近的整数", false),
    ROUND2("ROUND", "ROUND(x,y)", 2, "保留x小数点后y位的值，但截断时要进行四舍五入", false),
    COUNT("COUNT", "COUNT(x)", 4, "对x计数", false),
    SUM("SUM", "SUM(x)", 4, "对x求和", false),
    AVG("AVG", "AVG(x)", 4, "对x求平均值", false),
    MAX("MAX", "MAX(x)", 4, "对x求最大值", false),
    MIN("MIN", "MIN(x)", 4, "对x求最小值", false);

    private String name;// 显示名称。
    private String func;// 函数表达式。
    private int type;// 字段类型：0 文本，1 时间，2 数值，3 逻辑，4 聚合。
    private String desc;// 函数说明。
    private boolean isCustom;// 是否为自定义函数。

    /**
     * 构造 SQL 函数元数据
     */
    SQLFunctionsEnum(String name, String func, int type, String desc, boolean isCustom) {
        this.name = name;
        this.func = func;
        this.type = type;
        this.desc = desc;
        this.isCustom = isCustom;
    }

    /**
     * 获取函数显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置函数显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取函数表达式
     */
    public String getFunc() {
        return func;
    }

    /**
     * 设置函数表达式
     */
    public void setFunc(String func) {
        this.func = func;
    }

    /**
     * 获取函数适用字段类型
     */
    public int getType() {
        return type;
    }

    /**
     * 设置函数适用字段类型
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * 获取函数说明
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 设置函数说明
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * 判断是否为自定义函数
     */
    public boolean isCustom() {
        return isCustom;
    }

    /**
     * 设置是否为自定义函数
     */
    public void setCustom(boolean custom) {
        isCustom = custom;
    }
}
