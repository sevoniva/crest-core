package io.crest.exportCenter.util;

import io.crest.exportCenter.manage.ExportCenterLimitManage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
// 为静态导出流程提供统一的导出行数限制读取入口
public class ExportCenterUtils {


    private static ExportCenterLimitManage exportCenterLimitManage;

    @Resource(name = "exportCenterLimitManage")
    public void setExportCenterLimitManage(ExportCenterLimitManage exportCenterLimitManage) {
        ExportCenterUtils.exportCenterLimitManage = exportCenterLimitManage;
    }

    // type 对应视图、数据集等导出类型
    public static long getExportLimit(String type) {
        return exportCenterLimitManage.getExportLimit(type);
    }
}
