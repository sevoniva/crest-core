-- Crest Core 首版元数据表统一命名。
-- 本脚本只重命名 Crest 自有元数据表，Quartz 的 QRTZ_* 表保持不变。
-- 执行前请备份系统库；如果同时存在旧表和新表，脚本会中断以避免覆盖数据。

CREATE OR REPLACE PROCEDURE crest_rename_table_if_needed(
  old_table_name IN VARCHAR2,
  new_table_name IN VARCHAR2
) AS
  old_count NUMBER := 0;
  new_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO old_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(old_table_name);
  SELECT COUNT(1) INTO new_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(new_table_name);

  IF old_count = 1 AND new_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ' || old_table_name || ' RENAME TO ' || new_table_name;
  ELSIF old_count = 1 AND new_count = 1 THEN
    RAISE_APPLICATION_ERROR(-20001, '元数据表改名冲突：旧表和新表同时存在，请先人工确认数据');
  END IF;
END;
/

BEGIN
  crest_rename_table_if_needed('api_traffic', 'core_api_traffic_limit');
  crest_rename_table_if_needed('ref_area', 'core_reference_area');
  crest_rename_table_if_needed('ref_custom_area', 'core_reference_custom_area');
  crest_rename_table_if_needed('geo_area', 'core_geo_area');
  crest_rename_table_if_needed('geo_sub_area', 'core_geo_sub_area');
  crest_rename_table_if_needed('copilot_config', 'core_query_assistant_config');
  crest_rename_table_if_needed('copilot_message', 'core_query_assistant_message');
  crest_rename_table_if_needed('copilot_token', 'core_query_assistant_token');
  crest_rename_table_if_needed('dataset_group', 'core_dataset');
  crest_rename_table_if_needed('dataset_table', 'core_dataset_table');
  crest_rename_table_if_needed('dataset_field', 'core_dataset_field');
  crest_rename_table_if_needed('dataset_sql_log', 'core_dataset_sql_log');
  crest_rename_table_if_needed('dataset_sync_task', 'core_dataset_sync_task');
  crest_rename_table_if_needed('dataset_sync_task_log', 'core_dataset_sync_task_log');
  crest_rename_table_if_needed('data_source', 'core_datasource');
  crest_rename_table_if_needed('data_source_sync_task', 'core_datasource_sync_task');
  crest_rename_table_if_needed('data_source_sync_task_log', 'core_datasource_sync_task_log');
  crest_rename_table_if_needed('data_engine', 'core_datasource_engine');
  crest_rename_table_if_needed('data_source_driver', 'core_datasource_driver');
  crest_rename_table_if_needed('data_source_driver_jar', 'core_datasource_driver_jar');
  crest_rename_table_if_needed('data_source_finish_page', 'core_datasource_finish_page');
  crest_rename_table_if_needed('data_asset_profile', 'core_asset_profile');
  crest_rename_table_if_needed('bi_chart', 'core_chart_view');
  crest_rename_table_if_needed('bi_chart_snapshot', 'core_chart_view_snapshot');
  crest_rename_table_if_needed('bi_visualization', 'core_visualization');
  crest_rename_table_if_needed('bi_visualization_snapshot', 'core_visualization_snapshot');
  crest_rename_table_if_needed('visualization_background', 'core_visualization_background');
  crest_rename_table_if_needed('visualization_background_image', 'core_visualization_background_image');
  crest_rename_table_if_needed('visualization_link_jump', 'core_visualization_jump');
  crest_rename_table_if_needed('visualization_link_jump_info', 'core_visualization_jump_action');
  crest_rename_table_if_needed('visualization_link_jump_target_view_info', 'core_visualization_jump_target');
  crest_rename_table_if_needed('snapshot_visualization_link_jump', 'core_visualization_jump_snapshot');
  crest_rename_table_if_needed('snapshot_visualization_link_jump_info', 'core_visualization_jump_action_snapshot');
  crest_rename_table_if_needed('snapshot_visualization_link_jump_target_view_info', 'core_visualization_jump_target_snapshot');
  crest_rename_table_if_needed('visualization_linkage', 'core_visualization_linkage');
  crest_rename_table_if_needed('visualization_linkage_field', 'core_visualization_linkage_field');
  crest_rename_table_if_needed('snapshot_visualization_linkage', 'core_visualization_linkage_snapshot');
  crest_rename_table_if_needed('snapshot_visualization_linkage_field', 'core_visualization_linkage_field_snapshot');
  crest_rename_table_if_needed('visualization_outer_params', 'core_visualization_parameter');
  crest_rename_table_if_needed('visualization_outer_params_info', 'core_visualization_parameter_item');
  crest_rename_table_if_needed('visualization_outer_params_target_view_info', 'core_visualization_parameter_target');
  crest_rename_table_if_needed('snapshot_visualization_outer_params', 'core_visualization_parameter_snapshot');
  crest_rename_table_if_needed('snapshot_visualization_outer_params_info', 'core_visualization_parameter_item_snapshot');
  crest_rename_table_if_needed('snapshot_visualization_outer_params_target_view_info', 'core_visualization_parameter_target_snapshot');
  crest_rename_table_if_needed('visualization_report_filter', 'core_visualization_report_filter');
  crest_rename_table_if_needed('visualization_subject', 'core_visualization_theme');
  crest_rename_table_if_needed('visualization_watermark', 'core_visualization_watermark');
  crest_rename_table_if_needed('visualization_template', 'core_template');
  crest_rename_table_if_needed('visualization_template_category', 'core_template_category');
  crest_rename_table_if_needed('visualization_template_category_map', 'core_template_category_rel');
  crest_rename_table_if_needed('visualization_template_extend_data', 'core_template_view_data');
  crest_rename_table_if_needed('template_version', 'core_template_init_history');
  crest_rename_table_if_needed('export_task', 'core_export_task');
  crest_rename_table_if_needed('export_download_task', 'core_export_download');
  crest_rename_table_if_needed('font_asset', 'core_font_asset');
  crest_rename_table_if_needed('iam_menu', 'core_iam_menu');
  crest_rename_table_if_needed('iam_user', 'core_iam_user');
  crest_rename_table_if_needed('iam_org', 'core_iam_org');
  crest_rename_table_if_needed('iam_role', 'core_iam_role');
  crest_rename_table_if_needed('iam_user_org', 'core_iam_user_org');
  crest_rename_table_if_needed('iam_user_role', 'core_iam_user_role');
  crest_rename_table_if_needed('iam_role_menu_permission', 'core_iam_role_menu_permission');
  crest_rename_table_if_needed('iam_resource_index', 'core_iam_resource_index');
  crest_rename_table_if_needed('iam_resource_permission', 'core_iam_resource_permission');
  crest_rename_table_if_needed('auth_setting', 'core_auth_setting');
  crest_rename_table_if_needed('auth_sso_provider', 'core_auth_sso_provider');
  crest_rename_table_if_needed('auth_sso_identity_binding', 'core_auth_sso_identity_binding');
  crest_rename_table_if_needed('platform_token', 'core_auth_token');
  crest_rename_table_if_needed('crypto_key', 'core_crypto_key');
  crest_rename_table_if_needed('share_link', 'core_share_link');
  crest_rename_table_if_needed('share_ticket', 'core_share_ticket');
  crest_rename_table_if_needed('workspace_recent_resource', 'core_workspace_recent_resource');
  crest_rename_table_if_needed('workspace_favorite_resource', 'core_workspace_favorite_resource');
  crest_rename_table_if_needed('sys_setting', 'core_system_setting');
  crest_rename_table_if_needed('startup_job', 'core_system_startup_job');
  crest_rename_table_if_needed('threshold_rule', 'core_alert_rule');
  crest_rename_table_if_needed('threshold_instance', 'core_alert_instance');
  crest_rename_table_if_needed('webhook_config', 'core_webhook_config');
  crest_rename_table_if_needed('plugin_registry', 'core_plugin_registry');
  crest_rename_table_if_needed('audit_log', 'core_audit_log');
  crest_rename_table_if_needed('scheduled_task_queue_state', 'core_scheduled_task_queue_state');
END;
/

CREATE OR REPLACE PROCEDURE crest_merge_typeface_asset AS
  typeface_count NUMBER := 0;
  font_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO typeface_count FROM USER_TABLES WHERE TABLE_NAME = 'TYPEFACE_ASSET';
  SELECT COUNT(1) INTO font_count FROM USER_TABLES WHERE TABLE_NAME = 'CORE_FONT_ASSET';

  IF typeface_count = 1 AND font_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE typeface_asset RENAME TO core_font_asset';
  ELSIF typeface_count = 1 AND font_count = 1 THEN
    EXECUTE IMMEDIATE 'INSERT INTO core_font_asset (id, name, file_name, file_trans_name, is_default) SELECT id, name, file_name, file_trans_name, is_default FROM typeface_asset t WHERE NOT EXISTS (SELECT 1 FROM core_font_asset f WHERE f.id = t.id)';
    EXECUTE IMMEDIATE 'DROP TABLE typeface_asset';
  END IF;
END;
/

BEGIN
  crest_merge_typeface_asset;
END;
/

DROP PROCEDURE crest_merge_typeface_asset;
DROP PROCEDURE crest_rename_table_if_needed;
