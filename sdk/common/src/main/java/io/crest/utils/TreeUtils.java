package io.crest.utils;

import io.crest.constant.SortConstants;
import io.crest.model.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.util.Assert;

import java.text.Collator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 树结构工具类，负责列表转树、树节点转换和自定义排序
 */
@SuppressWarnings({"deprecation", "unchecked"})
public class TreeUtils {

    /**
     * 默认根节点标识
     */
    public final static String DEFAULT_ROOT = "root";
    /**
     * 层级路径分隔符
     */
    public final static String SEPARATOR = "-crest-";

    /**
     * 权限菜单国际化 key 前缀
     */
    private final static String I18N_PREFIX = "i18n_auth_menu.";

    /**
     * 将基础树模型列表合并为树并转换为目标结果类型
     */
    public static <T extends TreeResultModel, R extends TreeBaseModel> List<T> mergeTree(List<R> list, Class<T> tClass, boolean appendI18nPrefix) {
        AtomicBoolean rootExist = new AtomicBoolean(false);
        List<TreeModel> modelList = list.stream().map(item -> {
            TreeModel treeModel = new TreeModel(item);
            if (isRoot(treeModel)) {
                rootExist.set(true);
            }
            return treeModel;
        }).toList();
        List<TreeModel> modelResult = new ArrayList<>();
        Map<Long, List<TreeModel>> childMap = modelList.stream().collect(Collectors.groupingBy(TreeModel::getPid));
        List<Long> existedList = new ArrayList<>();
        modelList.forEach(po -> {
            List<TreeModel> children = null;
            if (CollectionUtils.isNotEmpty(children = childMap.get(po.getId()))) {
                po.setChildren(children);
                existedList.addAll(children.stream().map(TreeModel::getId).toList());
            }
        });
        if (CollectionUtils.isEmpty(modelList)) {
            return null;
        }
        List<TreeModel> floatingList = modelList.stream().filter(node -> !isRoot(node) && !existedList.contains(node.getId())).toList();
        if (CollectionUtils.isNotEmpty(existedList)) {
            modelResult = modelList.stream().filter(node -> !existedList.contains(node.getId())).toList();
        } else {
            modelResult = modelList;
        }
        if (rootExist.get() && CollectionUtils.isNotEmpty(floatingList)) {
            modelResult = modelResult.stream().filter(TreeUtils::isRoot).collect(Collectors.toList());
            TreeModel root = modelResult.get(0);
            if (root.getChildren() == null) {
                root.setChildren(new ArrayList<>());
            }
            root.getChildren().addAll(floatingList);
        }

        return convertTree(modelResult, tClass, appendI18nPrefix);
    }

    /**
     * 判断节点是否为默认根节点
     */
    private static boolean isRoot(TreeModel node) {
        return node.getId().equals(0L) && (ObjectUtils.isEmpty(node.getPid()) || node.getPid().equals(-1L));
    }

    /**
     * 将内部树节点递归转换为指定结果模型
     */
    public static <T extends TreeResultModel> List<T> convertTree(List<TreeModel> roots, Class<T> tClass, boolean appendI18nPrefix) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            TreeModel node = roots.get(i);
            if (appendI18nPrefix) {
                node.getData().setName(I18N_PREFIX + node.getName());
            }
            T instance = null;
            try {
                instance = tClass.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            T vo = BeanUtils.copyBean(instance, node.getData(), "children");
            result.add(vo);
            List<TreeModel> children = null;
            if (!CollectionUtils.isEmpty(children = node.getChildren())) {
                vo.setChildren(convertTree(children, tClass, appendI18nPrefix));
            }
        }
        return result;
    }

    /**
     * Description: rootPid 是根节点PID
     */
    public static <T extends ITreeBase> List<T> mergeTree(List<T> tree, Long... rootPid) {
        Assert.notNull(rootPid, "Root Pid cannot be null");
        if (CollectionUtils.isEmpty(tree)) {
            return null;
        }
        List<T> result = new ArrayList<>();
        // 构建id-节点map映射
        Map<Long, T> treePidMap = tree.stream().collect(Collectors.toMap(T::getId, t -> t));
        tree.stream().forEach(node -> {
            // 判断根节点
            if (Arrays.asList(rootPid).contains(node.getPid())) {
                result.add(node);
            } else {
                //找到父元素
                T parentNode = treePidMap.get(node.getPid());
                if (parentNode == null) {
                    // 可能出现 rootPid 更高的节点 这个操作相当于截断
                    return;
                }
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList());
                }
                parentNode.getChildren().add(node);
            }
        });
        return result;
    }


    /**
     * Description: rootPid 是根节点PID 档期那默认是0
     */
    public static <T extends ITreeBase> List<T> mergeTree(List<T> tree) {
        return mergeTree(tree, 0L);
    }


    /**
     * 按节点类型路径合并可能重复 ID 的树结构
     */
    public static <T extends ITreeBase> List<T> mergeDuplicateTree(List<T> tree, Long... rootPid) {
        Assert.notNull(rootPid, "Root Pid cannot be null");
        if (CollectionUtils.isEmpty(tree)) {
            return null;
        }
        List<T> result = new ArrayList<>();
        // 构建id-节点map映射
        Map<String, T> treePidMap = tree.stream().collect(Collectors.toMap(ITreeBase::getNodeType, t -> t));
        tree.stream().filter(item -> ObjectUtils.isNotEmpty(item.getId())).forEach(node -> {

            String nodeType = node.getNodeType();
            String[] links = nodeType.split(SEPARATOR);
            int length = links.length;
            int level = Integer.parseInt(links[length - 1]);
            // 判断根节点
            if (Arrays.asList(rootPid).contains(node.getPid()) && 0 == level) {
                result.add(node);
            } else {
                //找到父元素
                String[] pLinks = new String[level];
                System.arraycopy(links, 0, pLinks, 0, level);
                String parentType = Arrays.stream(pLinks).collect(Collectors.joining(SEPARATOR)) + TreeUtils.SEPARATOR + (level - 1);
                T parentNode = treePidMap.get(parentType);
                if (parentNode == null) {
                    // 可能出现 rootPid 更高的节点 这个操作相当于截断
                    return;
                }
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList());
                }
                parentNode.getChildren().add(node);
            }
        });
        return result;
    }

    /**
     * 按名称或时间规则排序业务节点
     */
    public static List<BusiNodeVO> customSortVO(List<BusiNodeVO> list, String sortType) {
        Collator collator = Collator.getInstance(Locale.CHINA);
        if (Strings.CI.equals(SortConstants.NAME_DESC, sortType)) {
            Set<BusiNodeVO> poSet = new TreeSet<>(Comparator.comparing(BusiNodeVO::getName, collator));
            poSet.addAll(list);
            return poSet.stream().collect(Collectors.toList());
        } else if (Strings.CI.equals(SortConstants.NAME_ASC, sortType)) {
            Set<BusiNodeVO> poSet = new TreeSet<>(Comparator.comparing(BusiNodeVO::getName, collator).reversed());
            poSet.addAll(list);
            return poSet.stream().collect(Collectors.toList());
        } else if (Strings.CI.equals(SortConstants.TIME_ASC, sortType)) {
            Collections.reverse(list);
            return list;
        } else {
            // 默认时间倒序
            return list;
        }
    }
}
