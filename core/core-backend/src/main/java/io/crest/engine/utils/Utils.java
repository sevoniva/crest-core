package io.crest.engine.utils;

import io.crest.constant.SQLConstants;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.datasource.utils.SqlPlaceholderUtils;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.i18n.Translator;
import io.crest.utils.JsonUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 构造、字段表达式解析与时间格式转换工具
 */
public class Utils {
    /**
     * 通用 SQL 注入风险检测正则
     */
    public static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("[\\'\";`]"),
            Pattern.compile("--\\s*|#"),
            Pattern.compile("\\b(or|and|union|select|insert|delete|update|drop|alter|exec|xp_cmdshell)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b\\d+\\s*=\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b1'\\s*=\\s*'1\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 过滤值专用 SQL 注入检测正则，允许 Arkhangel'sk、O'Brien 等包含单引号的合法数据
     */
  public static final List<Pattern> SQL_INJECTION_PATTERNS_FOR_VALUES =
      Arrays.asList(
          Pattern.compile("[\";`]"),
          Pattern.compile("--\\s*|#"),
          Pattern.compile(
              "\\b(or|and|union|select|insert|delete|update|drop|alter|exec|xp_cmdshell)\\b",
              Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\b\\d+\\s*=\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\b1'\\s*=\\s*'1\\b", Pattern.CASE_INSENSITIVE));

    /**
     * 判断排序方式是否需要参与 SQL 排序拼接
     *
     * @param sort 排序方式
     * @return 升序或降序时返回 true
     */
    public static boolean joinSort(String sort) {
        return (Strings.CI.equals(sort, "asc") || Strings.CI.equals(sort, "desc"));
    }

    /**
     * 解析跨源或单源场景下的计算字段表达式
     *
     * @param chartField 当前计算字段
     * @param tableObj SQL 表对象
     * @param originFields 原始字段列表
     * @param isCross 是否跨源
     * @param dsMap 数据源映射
     * @param paramMap 计算字段参数值
     * @param pluginManage 插件数据源管理器
     * @return 可拼接到 SQL 的字段表达式
     */
    public static String calcFieldRegex(DatasetTableFieldDTO chartField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, Map<String, String> paramMap, PluginManageApi pluginManage) {
        try {
            int i = 0;
            DsTypeDTO datasourceType = null;
            if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
                Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
                datasourceType = getDs(pluginManage, next.getValue().getType());
            }
            return buildCalcField(chartField, tableObj, originFields, i, isCross, datasourceType, paramMap, true, chartField.getOriginName());
        } catch (Exception e) {
            CrestException.throwException(Translator.get("i18n_field_circular_ref"));
        }
        return null;
    }

    /**
     * 使用简化的数据源类型映射解析计算字段表达式
     *
     * @param chartField 当前计算字段
     * @param tableObj SQL 表对象
     * @param originFields 原始字段列表
     * @param isCross 是否跨源
     * @param dsTypeMap 数据源类型映射
     * @param pluginManage 插件数据源管理器
     * @return 可拼接到 SQL 的字段表达式
     */
    public static String calcSimpleFieldRegex(DatasetTableFieldDTO chartField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, boolean isCross, Map<Long, String> dsTypeMap, PluginManageApi pluginManage) {
        try {
            int i = 0;
            DsTypeDTO datasourceType = null;
            if (dsTypeMap != null && dsTypeMap.entrySet().iterator().hasNext()) {
                Map.Entry<Long, String> next = dsTypeMap.entrySet().iterator().next();
                datasourceType = getDs(pluginManage, next.getValue());
            }
            return buildCalcField(chartField, tableObj, originFields, i, isCross, datasourceType, null, true, chartField.getOriginName());
        } catch (Exception e) {
            CrestException.throwException(Translator.get("i18n_field_circular_ref"));
        }
        return null;
    }

    /**
     * 递归展开计算字段中的字段引用和参数引用
     *
     * @param chartField 当前计算字段
     * @param tableObj SQL 表对象
     * @param originFields 原始字段列表
     * @param i 递归深度
     * @param isCross 是否跨源
     * @param datasourceType 数据源类型信息
     * @param paramMap 计算字段参数值
     * @param isFirst 是否为首次解析
     * @param fieldExpression 当前字段表达式
     * @return 展开后的字段表达式
     * @throws Exception 字段存在循环引用时抛出异常
     */
    public static String buildCalcField(DatasetTableFieldDTO chartField, SQLObj tableObj, List<DatasetTableFieldDTO> originFields, int i, boolean isCross, DsTypeDTO datasourceType, Map<String, String> paramMap, boolean isFirst, String fieldExpression) throws Exception {
        try {
            i++;
            if (i > 100) {
                CrestException.throwException(Translator.get("i18n_field_circular_error"));
            }
            String originField = getCalcField(chartField, originFields, isFirst, fieldExpression);
            originField = stripLineBreaks(originField);
            Set<String> ids = bracketContents(originField);
            if (CollectionUtils.isEmpty(ids)) {
                return originField;
            }
            // 替换参数
            if (ObjectUtils.isNotEmpty(paramMap)) {
                Set<Map.Entry<String, String>> entries = paramMap.entrySet();
                for (Map.Entry<String, String> ele : entries) {
                    originField = originField.replace("[" + ele.getKey() + "]", ele.getValue());
                }
            }
            // 替换字段引用
            for (DatasetTableFieldDTO ele : originFields) {
                if (Strings.CI.contains(originField, ele.getId() + "")) {
                    // 计算字段允许二次引用，这里递归查询完整引用链
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        String physicalFieldName = physicalFieldName(ele);
                        if (isCross) {
                            originField = originField.replace("[" + ele.getId() + "]", String.format(SQLConstants.FIELD_NAME, tableObj.getTableAlias(), physicalFieldName));
                        } else {
                            originField = originField.replace("[" + ele.getId() + "]", datasourceType.getPrefix() + tableObj.getTableAlias() + datasourceType.getSuffix() + "." + datasourceType.getPrefix() + physicalFieldName + datasourceType.getSuffix());
                        }
                    } else {
                        originField = originField.replace("[" + ele.getId() + "]", "(" + ele.getOriginName() + ")");
                        originField = buildCalcField(chartField, tableObj, originFields, i, isCross, datasourceType, paramMap, false, originField);
                    }
                }
            }
            return originField;
        } catch (CrestException e) {
            throw e;
        } catch (Exception e) {
            CrestException.throwException(Translator.get("i18n_field_circular_error"));
        }
        return null;
    }

    private static String physicalFieldName(DatasetTableFieldDTO field) {
        String identifier = StringUtils.firstNonBlank(field.getEngineFieldName(), field.getFieldShortName(), field.getDbFieldName());
        if (StringUtils.isBlank(identifier) || Strings.CI.equals(identifier, "null")) {
            CrestException.throwException(Translator.get("i18n_gauge_field_change"));
        }
        return identifier;
    }

    /**
     * 获取计算字段的原始表达式
     *
     * @param ele 当前字段
     * @param originFields 原始字段列表
     * @param isFirst 是否为首次解析
     * @param fieldExpression 当前字段表达式
     * @return 计算字段表达式
     */
    public static String getCalcField(DatasetTableFieldDTO ele, List<DatasetTableFieldDTO> originFields, boolean isFirst, String fieldExpression) {
        if (isFirst) {
            for (DatasetTableFieldDTO field : originFields) {
                if (Objects.equals(ele.getId(), field.getId())) {
                    return field.getOriginName();
                }
            }
            return "";
        } else {
            return fieldExpression;
        }
    }

    /**
     * 将前端逻辑连接符转换为 SQL 连接符
     *
     * @param logic 前端逻辑连接符
     * @return SQL 逻辑连接符
     */
    public static String getLogic(String logic) {
        if (logic != null) {
            switch (logic) {
                case "and":
                    return "AND";
                case "or":
                    return "OR";
            }
        }
        return "AND";
    }

    /**
     * 将日期分组样式转换为 SQL 日期格式模板
     *
     * @param dateStyle 日期分组样式
     * @param datePattern 日期连接符模式
     * @return 日期格式模板
     */
    public static String transDateFormat(String dateStyle, String datePattern) {
        String split = "-";
        if (Strings.CI.equals(datePattern, "date_sub")) {
            split = "-";
        } else if (Strings.CI.equals(datePattern, "date_split")) {
            split = "/";
        } else {
            split = "-";
        }

        if (StringUtils.isEmpty(dateStyle)) {
            return "yyyy-MM-dd HH:mm:ss";
        }

        switch (dateStyle) {
            case "y":
                return "yyyy";
            case "y_Q":
                return "CONCAT(%s,'" + split + "',%s)";
            case "y_M":
                return "yyyy" + split + "MM";
            case "y_W":
                return "%Y" + split + "%u";
            case "y_M_d":
                return "yyyy" + split + "MM" + split + "dd";
            case "M_d":
                return "MM" + split + "dd";
            case "H_m_s":
                return "HH:mm:ss";
            case "y_M_d_H":
                return "yyyy" + split + "MM" + split + "dd" + " HH";
            case "y_M_d_H_m":
                return "yyyy" + split + "MM" + split + "dd" + " HH:mm";
            case "y_M_d_H_m_s":
                return "yyyy" + split + "MM" + split + "dd" + " HH:mm:ss";
            default:
                return "yyyy-MM-dd HH:mm:ss";
        }
    }

    /**
     * 将过滤操作符转换为 SQL 条件片段
     *
     * @param term 过滤操作符
     * @return SQL 条件片段
     */
    public static String transFilterTerm(String term) {
        switch (term) {
            case "eq":
                return " = ";
            case "not_eq":
                return " <> ";
            case "lt":
                return " < ";
            case "le":
                return " <= ";
            case "gt":
                return " > ";
            case "ge":
                return " >= ";
            case "in":
                return " IN ";
            case "not in":
                return " NOT IN ";
            case "like":
                return " LIKE ";
            case "not like":
                return " NOT LIKE ";
            case "null":
                return " IS NULL ";
            case "not_null":
                return " IS NOT NULL ";
            case "empty":
                return " = ";
            case "not_empty":
                return " <> ";
            case "between":
                return " BETWEEN ";
            default:
                return "";
        }
    }

    /**
     * 检查计算字段表达式是否存在循环引用
     *
     * @param originField 原始字段表达式
     * @param fields 字段列表
     */
    public static void checkCircularReference(String originField, List<DatasetTableFieldDTO> fields) {
        originField = stripLineBreaks(originField);
        for (String id : bracketContents(originField)) {
            Set<String> ids = new HashSet<>();
            for (DatasetTableFieldDTO ele : fields) {
                if (Strings.CI.contains(id, ele.getId() + "")) {
                    if (ids.contains(id)) {
                        CrestException.throwException(Translator.get("i18n_field_circular_ref"));
                    }
                    ids.add(id);
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        originField = originField.replace("[" + ele.getId() + "]", ele.getOriginName());
                        checkField(ids, originField, fields);
                    }
                }
            }
        }
    }

    /**
     * 递归检查字段引用链是否存在重复引用
     *
     * @param ids 已访问字段 ID 集合
     * @param originField 当前字段表达式
     * @param fields 字段列表
     */
    public static void checkField(Set<String> ids, String originField, List<DatasetTableFieldDTO> fields) {
        originField = stripLineBreaks(originField);
        for (String id : bracketContents(originField)) {
            for (DatasetTableFieldDTO ele : fields) {
                if (Strings.CI.contains(id, ele.getId() + "")) {
                    if (ids.contains(id)) {
                        CrestException.throwException(Translator.get("i18n_field_circular_ref"));
                    }
                    ids.add(id);
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        originField = originField.replace("[" + ele.getId() + "]", ele.getOriginName());
                        checkField(ids, originField, fields);
                    }
                }
            }
        }
    }

    /**
     * 判断字段表达式是否调用了指定函数
     *
     * @param func 函数名称
     * @param originField 字段表达式
     * @return 调用指定函数时返回 true
     */
    public static boolean matchFunction(String func, String originField) {
        String pattern = Pattern.quote(func) + "\\s*\\(";
        Pattern r = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher m = r.matcher(stripLineBreaks(originField));
        while (m.find()) {
            return true;
        }
        return false;
    }

    /**
     * 判断数据源列表是否需要强制生成排序
     *
     * @param dsList 数据源类型列表
     * @return 存在需要排序的数据源时返回 true
     */
    public static boolean isNeedOrder(List<String> dsList) {
        String[] list = {"sqlServer", "db2", "impala", "doris"};
        List<String> strings = Arrays.asList(list);
        List<String> collect = strings.stream().filter(dsList::contains).collect(Collectors.toList());
        return ObjectUtils.isNotEmpty(collect);
    }

    /**
     * 判断数据源映射是否表示跨源查询
     *
     * @param dsMap 数据源映射
     * @return 数据源数量不为 1 时返回 true
     */
    public static boolean isCrossDs(Map<Long, DatasourceSchemaDTO> dsMap) {
        return dsMap.size() != 1;
    }

    /**
     * 将单源查询中的内部 schema 别名转换为真实数据库标识
     *
     * @param sql 原始 SQL
     * @param dsMap 数据源映射
     * @return 处理后的 SQL
     */
    public static String replaceSchemaAlias(String sql, Map<Long, DatasourceSchemaDTO> dsMap) {
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        Map map = StringUtils.isBlank(value.getConfiguration()) ? null : JsonUtil.parseObject(value.getConfiguration(), Map.class);
        String schemaAlias = value.getSchemaAlias();
        if (StringUtils.isBlank(schemaAlias)) {
            return sql;
        }
        Object schemaValue = map == null ? null : map.get("schema");
        String schema = schemaValue == null ? null : schemaValue.toString().trim();
        if (StringUtils.isNotBlank(schema)) {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(value.getType());
            return SqlPlaceholderUtils.replaceIdentifier(sql, schemaAlias, datasourceType.getPrefix() + schema + datasourceType.getSuffix());
        }
        return SqlPlaceholderUtils.removeIdentifierPrefix(sql, schemaAlias);
    }

    /**
     * 移除表达式中的换行和制表符
     *
     * @param value 原始文本
     * @return 去除换行后的文本
     */
    private static String stripLineBreaks(String value) {
        return value == null ? null : value.replace("\t", "").replace("\n", "").replace("\r", "");
    }

    /**
     * 提取表达式方括号中的字段或参数标识
     *
     * @param value 原始表达式
     * @return 方括号内容集合
     */
    private static Set<String> bracketContents(String value) {
        Set<String> contents = new LinkedHashSet<>();
        if (StringUtils.isEmpty(value)) {
            return contents;
        }
        int searchFrom = 0;
        while (searchFrom < value.length()) {
            int start = value.indexOf('[', searchFrom);
            if (start < 0) {
                break;
            }
            int end = value.indexOf(']', start + 1);
            if (end < 0) {
                break;
            }
            contents.add(value.substring(start + 1, end));
            searchFrom = end + 1;
        }
        return contents;
    }

    /**
     * 将常见日期时间文本转换为毫秒时间戳
     *
     * @param value 日期时间文本
     * @return 解析成功后的时间戳，失败时返回 0
     */
    public static long allDateFormat2Long(String value) {
        String split = "-";
        if (value != null && value.contains("/")) {
            split = "/";
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm:ss");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
            return simpleDateFormat.parse(value).getTime();
        } catch (Exception e) {
        }
        return 0;
    }

    /**
     * 根据源格式和目标格式处理时间文本
     *
     * @param time 原始时间文本
     * @param sourceFormat 源格式
     * @param targetFormat 目标格式
     * @return 处理后的时间文本
     */
    public static String parseTime(String time, String sourceFormat, String targetFormat) {
        if (Strings.CI.equals(sourceFormat, targetFormat)) {
            String[] s = time.split(" ");
            if (s.length > 1) {
                time = s[1];
            } else {
                time = s[0];
            }
        }
        return time;
    }

    /**
     * 将日期时间文本解析为对应时间粒度的起止时间戳
     *
     * @param value 日期时间文本
     * @return 包含 startTime 和 endTime 的时间范围
     */
    public static Map<String, Long> parseDateTimeValue(String value) {
        Map<String, Long> map = new LinkedHashMap<>();
        long startTime = 0;
        long endTime = 0;

        String split = "-";
        if (value != null && value.contains("/")) {
            split = "/";
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm:ss");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + 999;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH:mm");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd HH");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (60 * 60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + 999;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM" + split + "dd");
            startTime = simpleDateFormat.parse(value).getTime();
            endTime = startTime + (24 * 60 * 60 * 1000 - 1);

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy" + split + "MM");
            Date parse = simpleDateFormat.parse(value);
            startTime = parse.getTime();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parse);
            calendar.add(Calendar.MONTH, 1);
            endTime = calendar.getTime().getTime() - 1;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
            Date parse = simpleDateFormat.parse(value);
            startTime = parse.getTime();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(parse);
            calendar.add(Calendar.YEAR, 1);
            endTime = calendar.getTime().getTime() - 1;

            map.put("startTime", startTime);
            map.put("endTime", endTime);
            return map;
        } catch (Exception e) {
        }

        map.put("startTime", startTime);
        map.put("endTime", endTime);
        return map;
    }

    /**
     * 将毫秒时间戳转换为完整日期时间文本
     *
     * @param ts 毫秒时间戳
     * @return 日期时间文本
     */
    public static String transLong2Str(Long ts) {
        Date date = new Date(ts);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return simpleDateFormat.format(date);
    }

    /**
     * 将毫秒时间戳转换为日期文本
     *
     * @param ts 毫秒时间戳
     * @return 日期文本
     */
    public static String transLong2StrShort(Long ts) {
        Date date = new Date(ts);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return simpleDateFormat.format(date);
    }

    /**
     * 收集计算字段中声明的参数
     *
     * @param list 数据集字段列表
     * @return 计算字段参数列表
     */
    public static List<CalParam> getParams(List<DatasetTableFieldDTO> list) {
        if (ObjectUtils.isEmpty(list)) return Collections.emptyList();
        List<CalParam> param = new ArrayList<>();
        for (DatasetTableFieldDTO dto : list) {
            if (Objects.equals(dto.getExtField(), ExtFieldConstant.EXT_CALC) && ObjectUtils.isNotEmpty(dto.getParams())) {
                param.addAll(dto.getParams());
            }
        }
        return param;
    }

    /**
     * 合并字段参数和图表参数，图表参数可覆盖同名字段参数
     *
     * @param fieldParam 字段级参数
     * @param chartParam 图表级参数
     * @return 参数 ID 到参数值的映射
     */
    public static Map<String, String> mergeParam(List<CalParam> fieldParam, List<CalParam> chartParam) {
        Map<String, String> map = new HashMap<>();
        if (ObjectUtils.isNotEmpty(fieldParam)) {
            for (CalParam param : fieldParam) {
                map.put(param.getId(), param.getValue());
            }
        }
        if (ObjectUtils.isNotEmpty(chartParam)) {
            for (CalParam param : chartParam) {
                map.put(param.getId(), param.getValue());
            }
        }
        return map;
    }

    /**
     * 获取内置或插件数据源的 SQL 标识符配置
     *
     * @param pluginManage 插件数据源管理器
     * @param type 数据源类型
     * @return 数据源类型配置
     */
    private static DsTypeDTO getDs(PluginManageApi pluginManage, String type) {
        DsTypeDTO dto = new DsTypeDTO();
        try {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(type);
            dto.setType(type);
            dto.setName(datasourceType.getName());
            dto.setCatalog(datasourceType.getCatalog());
            dto.setPrefix(datasourceType.getPrefix());
            dto.setSuffix(datasourceType.getSuffix());
            return dto;
        } catch (Exception e) {
            List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
            for (PluginDatasourceVO vo : pluginDatasourceList) {
                if (Strings.CI.equals(vo.getType(), type)) {
                    dto.setType(type);
                    dto.setName(vo.getName());
                    dto.setCatalog(vo.getCategory());
                    dto.setPrefix(vo.getPrefix());
                    dto.setSuffix(vo.getSuffix());
                    return dto;
                }
            }
        }
        return null;
    }

    /**
     * 将字段分组配置转换为 SQL CASE 表达式
     *
     * @param dto 分组字段配置
     * @param fields 数据集字段列表
     * @param isCross 是否跨源
     * @param dsMap 数据源映射
     * @param pluginManage 插件数据源管理器
     * @return SQL CASE 表达式
     */
    public static String transGroupFieldToSql(DatasetTableFieldDTO dto, List<DatasetTableFieldDTO> fields, boolean isCross, Map<Long, DatasourceSchemaDTO> dsMap, PluginManageApi pluginManage) {
        // 从fields里取最新的dto
        for (DatasetTableFieldDTO fieldDTO : fields) {
            if (Objects.equals(dto.getId(), fieldDTO.getId())) {
                dto.setGroupList(fieldDTO.getGroupList());
                dto.setOtherGroup(fieldDTO.getOtherGroup());
                break;
            }
        }
        // 获取被分组字段的原始字段配置
        DatasetTableFieldDTO originField = null;
        for (DatasetTableFieldDTO ele : fields) {
            if (Objects.equals(ele.getId(), Long.valueOf(dto.getOriginName()))) {
                originField = ele;
                break;
            }
        }
        if (originField == null) {
            CrestException.throwException("Field not exists");
        }

        DsTypeDTO datasourceType = null;
        if (dsMap != null && dsMap.entrySet().iterator().hasNext()) {
            Map.Entry<Long, DatasourceSchemaDTO> next = dsMap.entrySet().iterator().next();
            datasourceType = getDs(pluginManage, next.getValue().getType());
        }
        if (datasourceType == null) {
            CrestException.throwException("Datasource not exists");
        }

        String fieldName;
        if (isCross) {
            fieldName = originField.getEngineFieldName();
        } else {
            fieldName = datasourceType.getPrefix() + originField.getEngineFieldName() + datasourceType.getSuffix();
        }

        StringBuilder exp = new StringBuilder();
        exp.append(" (CASE ");
        if (originField.getFieldType() == 0) {
            for (FieldGroupDTO fieldGroupDTO : dto.getGroupList()) {
                exp.append(" WHEN ");
                for (int i = 0; i < fieldGroupDTO.getText().size(); i++) {
                    String value = fieldGroupDTO.getText().get(i);
                    exp.append(fieldName).append(" = ").append("'").append(transValue(value)).append("'");
                    if (i < fieldGroupDTO.getText().size() - 1) {
                        exp.append(" OR ");
                    }
                }
                exp.append(" THEN '").append(transValue(fieldGroupDTO.getName())).append("'");
            }
        } else if (originField.getFieldType() == 1) {
            for (FieldGroupDTO fieldGroupDTO : dto.getGroupList()) {
                exp.append(" WHEN ");
                exp.append(fieldName).append(" >= ").append("'").append(fieldGroupDTO.getStartTime()).append("'");
                exp.append(" AND ");
                exp.append(fieldName).append(" <= ").append("'").append(fieldGroupDTO.getEndTime()).append("'");
                exp.append(" THEN '").append(transValue(fieldGroupDTO.getName())).append("'");
            }
        } else if (originField.getFieldType() == 2 || originField.getFieldType() == 3 || originField.getFieldType() == 4) {
            for (FieldGroupDTO fieldGroupDTO : dto.getGroupList()) {
                exp.append(" WHEN ");
                exp.append(fieldName).append(Strings.CI.equals(fieldGroupDTO.getMinTerm(), "le") ? " >= " : " > ").append(fieldGroupDTO.getMin());
                exp.append(" AND ");
                exp.append(fieldName).append(Strings.CI.equals(fieldGroupDTO.getMaxTerm(), "le") ? " <= " : " < ").append(fieldGroupDTO.getMax());
                exp.append(" THEN '").append(transValue(fieldGroupDTO.getName())).append("'");
            }
        }
        exp.append(" ELSE ").append("'").append(transValue(dto.getOtherGroup())).append("'").append(" END) ");
        return exp.toString();
    }

    /**
     * 转义用于 SQL 字面量的普通文本
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    public static String transValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "''").replace("\n", "\\n");
    }

    /**
     * 检测过滤值中的 SQL 注入风险，该方法允许合法单引号
     *
     * 使用该方法后，必须在后续调用 {@link #transValue(String)} 进行字面量转义
     *
     * @param value 待检测的过滤值
     */
    public static void validateSqlInjectionRisk(String value) {
        String normalized = StringUtils.defaultString(value);
        if (StringUtils.isEmpty(normalized)) {
            return;
        }
        for (Pattern pattern : SQL_INJECTION_PATTERNS_FOR_VALUES) {
            if (pattern.matcher(normalized).find()) {
                CrestException.throwException("Illegal filter value");
            }
        }
    }
}
