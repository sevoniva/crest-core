package io.crest.commons.constants;

/**
 * 操作审计使用的动作类型和资源类型编码。
 */
public class OptConstants {

    public static final class OPT_TYPE {
        // 新建资源。
        public static final int NEW = 1;
        // 更新资源。
        public static final int UPDATE = 2;
        // 删除资源。
        public static final int DELETE = 3;
    }

    public static final class OPT_RESOURCE_TYPE {
        // 可视化资源。
        public static final int VISUALIZATION = 1;
        // 仪表板。
        public static final int DASHBOARD = 2;
        // 数据大屏。
        public static final int DATA_VISUALIZATION = 3;
        // 数据集。
        public static final int DATASET = 4;
        // 数据源。
        public static final int DATASOURCE = 5;
        // 模板。
        public static final int TEMPLATE = 6;
    }

}
