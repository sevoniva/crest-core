package io.crest.asset;

import io.crest.exception.CrestException;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 数据资产类型、分页和治理状态工具
 */
public final class DataAssetUtils {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static final String TYPE_DATASOURCE = "datasource";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_DATASET = "dataset";
    public static final String TYPE_CHART = "chart";
    public static final String TYPE_PANEL = "panel";
    public static final String TYPE_SCREEN = "screen";
    public static final String TYPE_SHARE = "share";
    public static final String TYPE_CACHE_TASK = "cacheTask";

    private static final Set<String> ASSET_TYPES = Set.of(
            TYPE_DATASOURCE,
            TYPE_TABLE,
            TYPE_DATASET,
            TYPE_CHART,
            TYPE_PANEL,
            TYPE_SCREEN,
            TYPE_SHARE
    );

    private DataAssetUtils() {
    }

    /**
     * 规范化页码，确保页码从 1 开始
     */
    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 规范化分页大小，并限制最大页大小
     */
    public static int normalizePageSize(Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * 规范化资产类型别名
     */
    public static String normalizeAssetType(String assetType) {
        if (StringUtils.isBlank(assetType) || "all".equalsIgnoreCase(assetType)) {
            return null;
        }
        String normalized = assetType.trim().toLowerCase(Locale.ROOT);
        if ("datav".equals(normalized) || "screen".equals(normalized)) {
            return TYPE_SCREEN;
        }
        if ("dashboard".equals(normalized) || "panel".equals(normalized)) {
            return TYPE_PANEL;
        }
        if (!ASSET_TYPES.contains(normalized)) {
            CrestException.throwException("资产类型不正确");
        }
        return normalized;
    }

    /**
     * 获取必填资产类型，缺失时抛出业务异常
     */
    public static String requireAssetType(String assetType) {
        String normalized = normalizeAssetType(assetType);
        if (normalized == null) {
            CrestException.throwException("资产类型不能为空");
        }
        return normalized;
    }

    /**
     * 将资产类型转换为资源类型
     */
    public static String resourceTypeForAsset(String assetType) {
        if (TYPE_SCREEN.equals(assetType)) {
            return TYPE_SCREEN;
        }
        if (TYPE_PANEL.equals(assetType)) {
            return TYPE_PANEL;
        }
        if (TYPE_DATASOURCE.equals(assetType) || TYPE_DATASET.equals(assetType)) {
            return assetType;
        }
        return null;
    }

    /**
     * 获取资产类型的中文显示名称
     */
    public static String assetTypeLabel(String assetType) {
        return switch (StringUtils.defaultString(assetType)) {
            case TYPE_DATASOURCE -> "数据源";
            case TYPE_TABLE -> "数据表";
            case TYPE_DATASET -> "数据集";
            case TYPE_CHART -> "图表";
            case TYPE_PANEL -> "仪表盘";
            case TYPE_SCREEN -> "数据大屏";
            case TYPE_SHARE -> "分享链接";
            case TYPE_CACHE_TASK -> "缓存任务";
            default -> "资产";
        };
    }

    /**
     * 根据认证、推荐和废弃标记规范化治理状态
     */
    public static GovernanceStatus normalizeGovernanceStatus(Boolean certified, Boolean recommended, Boolean deprecated) {
        boolean deprecatedStatus = Boolean.TRUE.equals(deprecated);
        if (deprecatedStatus) {
            return new GovernanceStatus(false, false, true);
        }
        return new GovernanceStatus(Boolean.TRUE.equals(certified), Boolean.TRUE.equals(recommended), false);
    }

    /**
     * 返回空治理状态
     */
    public static GovernanceStatus emptyGovernanceStatus() {
        return new GovernanceStatus(false, false, false);
    }

    /**
     * 数据资产治理状态
     */
    public record GovernanceStatus(boolean certified, boolean recommended, boolean deprecated) {
    }
}
