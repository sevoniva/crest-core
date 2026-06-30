-- Crest Core 首版运行配置标识标准化。
-- 本脚本在元数据表统一命名后执行，处理已保存的数据集、图表、可视化、模板和外部参数配置。

CREATE OR REPLACE PROCEDURE crest_replace_text_if_needed(
  target_table_name IN VARCHAR2,
  target_column_name IN VARCHAR2,
  old_value IN VARCHAR2,
  new_value IN VARCHAR2
) AS
  column_count NUMBER := 0;
  column_type VARCHAR2(128);
  update_sql VARCHAR2(4000);
BEGIN
  SELECT COUNT(1) INTO column_count
  FROM USER_TAB_COLUMNS
  WHERE TABLE_NAME = UPPER(target_table_name)
    AND COLUMN_NAME = UPPER(target_column_name);

  IF column_count = 1 THEN
    SELECT DATA_TYPE INTO column_type
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = UPPER(target_table_name)
      AND COLUMN_NAME = UPPER(target_column_name);

    IF column_type IN ('CLOB', 'NCLOB') THEN
      update_sql := 'UPDATE ' || target_table_name ||
        ' SET ' || target_column_name || ' = REPLACE(' || target_column_name || ', :old_value, :new_value)' ||
        ' WHERE DBMS_LOB.INSTR(' || target_column_name || ', :old_value) > 0';
    ELSE
      update_sql := 'UPDATE ' || target_table_name ||
        ' SET ' || target_column_name || ' = REPLACE(' || target_column_name || ', :old_value, :new_value)' ||
        ' WHERE ' || target_column_name || ' LIKE ''%'' || :old_value || ''%''';
    END IF;

    EXECUTE IMMEDIATE update_sql USING old_value, new_value, old_value;
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_normalize_config_column(
  target_table_name IN VARCHAR2,
  target_column_name IN VARCHAR2
) AS
BEGIN
  crest_replace_text_if_needed(target_table_name, target_column_name, '|DE|', '|DATASET_PARAM|');
  crest_replace_text_if_needed(target_table_name, target_column_name, '-de-', ' > ');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'DE_CAST_DATE_FORMAT', 'CREST_CAST_DATE_FORMAT');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'DE_UNIX_TIMESTAMP', 'CREST_UNIX_TIMESTAMP');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'DE_STR_TO_DATE', 'CREST_STR_TO_DATE');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'DE_FROM_UNIXTIME', 'CREST_FROM_UNIXTIME');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'DE_DATE_FORMAT', 'CREST_DATE_FORMAT');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-rich-text', 'rich-text');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-reset-button', 'crest-reset-button');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-button', 'crest-button');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-template-data', 'crest-template-data');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-report-filter-', 'crest-report-filter-');
  crest_replace_text_if_needed(target_table_name, target_column_name, 'de-canvas-', 'crest-canvas-');
END;
/

BEGIN
  -- 外部参数和跳转目标字段只保存字段 ID。
  crest_replace_text_if_needed('core_visualization_parameter_target', 'target_field_id', '|DE|', '|DATASET_PARAM|');
  crest_replace_text_if_needed('core_visualization_parameter_target_snapshot', 'target_field_id', '|DE|', '|DATASET_PARAM|');
  crest_replace_text_if_needed('core_visualization_jump_target', 'target_field_id', '|DE|', '|DATASET_PARAM|');
  crest_replace_text_if_needed('core_visualization_jump_target_snapshot', 'target_field_id', '|DE|', '|DATASET_PARAM|');

  -- 外部参数默认值可能保存树层级值。
  crest_normalize_config_column('core_visualization_parameter_item', 'default_value');
  crest_normalize_config_column('core_visualization_parameter_item_snapshot', 'default_value');

  -- 数据集计算字段和数据表配置可能保存内置日期函数。
  crest_normalize_config_column('core_dataset_field', 'origin_name');
  crest_normalize_config_column('core_dataset_field', 'group_list');
  crest_normalize_config_column('core_dataset_field', 'other_group');
  crest_normalize_config_column('core_dataset_field', 'params');
  crest_normalize_config_column('core_dataset_table', 'info');
  crest_normalize_config_column('core_dataset_table', 'sql_variable_details');

  -- 图表字段、过滤、高级配置和样式配置。
  crest_normalize_config_column('core_chart_view', 'x_axis');
  crest_normalize_config_column('core_chart_view', 'x_axis_ext');
  crest_normalize_config_column('core_chart_view', 'y_axis');
  crest_normalize_config_column('core_chart_view', 'y_axis_ext');
  crest_normalize_config_column('core_chart_view', 'ext_stack');
  crest_normalize_config_column('core_chart_view', 'ext_bubble');
  crest_normalize_config_column('core_chart_view', 'ext_label');
  crest_normalize_config_column('core_chart_view', 'ext_tooltip');
  crest_normalize_config_column('core_chart_view', 'custom_attr');
  crest_normalize_config_column('core_chart_view', 'custom_attr_mobile');
  crest_normalize_config_column('core_chart_view', 'custom_style');
  crest_normalize_config_column('core_chart_view', 'custom_style_mobile');
  crest_normalize_config_column('core_chart_view', 'custom_filter');
  crest_normalize_config_column('core_chart_view', 'drill_fields');
  crest_normalize_config_column('core_chart_view', 'senior');
  crest_normalize_config_column('core_chart_view', 'view_fields');
  crest_normalize_config_column('core_chart_view', 'flow_map_start_name');
  crest_normalize_config_column('core_chart_view', 'flow_map_end_name');
  crest_normalize_config_column('core_chart_view', 'ext_color');
  crest_normalize_config_column('core_chart_view', 'sort_priority');

  crest_normalize_config_column('core_chart_view_snapshot', 'x_axis');
  crest_normalize_config_column('core_chart_view_snapshot', 'x_axis_ext');
  crest_normalize_config_column('core_chart_view_snapshot', 'y_axis');
  crest_normalize_config_column('core_chart_view_snapshot', 'y_axis_ext');
  crest_normalize_config_column('core_chart_view_snapshot', 'ext_stack');
  crest_normalize_config_column('core_chart_view_snapshot', 'ext_bubble');
  crest_normalize_config_column('core_chart_view_snapshot', 'ext_label');
  crest_normalize_config_column('core_chart_view_snapshot', 'ext_tooltip');
  crest_normalize_config_column('core_chart_view_snapshot', 'custom_attr');
  crest_normalize_config_column('core_chart_view_snapshot', 'custom_attr_mobile');
  crest_normalize_config_column('core_chart_view_snapshot', 'custom_style');
  crest_normalize_config_column('core_chart_view_snapshot', 'custom_style_mobile');
  crest_normalize_config_column('core_chart_view_snapshot', 'custom_filter');
  crest_normalize_config_column('core_chart_view_snapshot', 'drill_fields');
  crest_normalize_config_column('core_chart_view_snapshot', 'senior');
  crest_normalize_config_column('core_chart_view_snapshot', 'view_fields');
  crest_normalize_config_column('core_chart_view_snapshot', 'flow_map_start_name');
  crest_normalize_config_column('core_chart_view_snapshot', 'flow_map_end_name');
  crest_normalize_config_column('core_chart_view_snapshot', 'ext_color');
  crest_normalize_config_column('core_chart_view_snapshot', 'sort_priority');

  -- 大屏、仪表盘、定时报告和模板配置。
  crest_normalize_config_column('core_visualization', 'canvas_style_data');
  crest_normalize_config_column('core_visualization', 'component_data');
  crest_normalize_config_column('core_visualization_snapshot', 'canvas_style_data');
  crest_normalize_config_column('core_visualization_snapshot', 'component_data');
  crest_normalize_config_column('core_visualization_report_filter', 'filter_info');

  crest_normalize_config_column('core_template', 'template_style');
  crest_normalize_config_column('core_template', 'template_data');
  crest_normalize_config_column('core_template', 'dynamic_data');
  crest_normalize_config_column('core_template', 'app_data');
  crest_normalize_config_column('core_template_view_data', 'view_details');
END;
/

DROP PROCEDURE crest_normalize_config_column;
DROP PROCEDURE crest_replace_text_if_needed;
