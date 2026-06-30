-- Crest Core v1.0.0 OceanBase Oracle 空库一次性初始化 SQL。
-- 仅适用于 Crest Core 首版全新系统库。
-- 本文件直接使用 v1.0.0 最终命名，不包含升级过程中的重命名 SQL。
-- 本文件由 scripts/generate-ob-oracle-init-schema.mjs 生成。

-- ----------------------------------------------------------------------
-- Section 1
-- ----------------------------------------------------------------------
-- Crest Core v1.0.0 fresh-install baseline for OceanBase Oracle tenants.
-- This file is only for new Crest Core system schemas.

CREATE TABLE core_schedule_blob_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  BLOB_DATA BLOB,
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_schedule_calendars (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  CALENDAR_NAME VARCHAR2(200) NOT NULL,
  CALENDAR BLOB NOT NULL,
  PRIMARY KEY (SCHED_NAME,CALENDAR_NAME)
);

CREATE TABLE core_schedule_cron_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  CRON_EXPRESSION VARCHAR2(200) NOT NULL,
  TIME_ZONE_ID VARCHAR2(80) DEFAULT NULL,
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_schedule_fired_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  ENTRY_ID VARCHAR2(95) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  INSTANCE_NAME VARCHAR2(200) NOT NULL,
  FIRED_TIME NUMBER(19,0) NOT NULL,
  SCHED_TIME NUMBER(19,0) NOT NULL,
  PRIORITY NUMBER(10,0) NOT NULL,
  STATE VARCHAR2(16) NOT NULL,
  JOB_NAME VARCHAR2(200) DEFAULT NULL,
  JOB_GROUP VARCHAR2(200) DEFAULT NULL,
  IS_NONCONCURRENT VARCHAR2(1) DEFAULT NULL,
  REQUESTS_RECOVERY VARCHAR2(1) DEFAULT NULL,
  PRIMARY KEY (SCHED_NAME,ENTRY_ID)
);

CREATE TABLE core_schedule_job_details (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  JOB_NAME VARCHAR2(200) NOT NULL,
  JOB_GROUP VARCHAR2(200) NOT NULL,
  DESCRIPTION VARCHAR2(250) DEFAULT NULL,
  JOB_CLASS_NAME VARCHAR2(250) NOT NULL,
  IS_DURABLE VARCHAR2(1) NOT NULL,
  IS_NONCONCURRENT VARCHAR2(1) NOT NULL,
  IS_UPDATE_DATA VARCHAR2(1) NOT NULL,
  REQUESTS_RECOVERY VARCHAR2(1) NOT NULL,
  JOB_DATA BLOB,
  PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
);

CREATE TABLE core_schedule_locks (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  LOCK_NAME VARCHAR2(40) NOT NULL,
  PRIMARY KEY (SCHED_NAME,LOCK_NAME)
);

CREATE TABLE core_schedule_paused_trigger_groups (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_schedule_scheduler_state (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  INSTANCE_NAME VARCHAR2(200) NOT NULL,
  LAST_CHECKIN_TIME NUMBER(19,0) NOT NULL,
  CHECKIN_INTERVAL NUMBER(19,0) NOT NULL,
  PRIMARY KEY (SCHED_NAME,INSTANCE_NAME)
);

CREATE TABLE core_schedule_simple_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  REPEAT_COUNT NUMBER(19,0) NOT NULL,
  REPEAT_INTERVAL NUMBER(19,0) NOT NULL,
  TIMES_TRIGGERED NUMBER(19,0) NOT NULL,
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_schedule_simprop_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  STR_PROP_1 VARCHAR2(512) DEFAULT NULL,
  STR_PROP_2 VARCHAR2(512) DEFAULT NULL,
  STR_PROP_3 VARCHAR2(512) DEFAULT NULL,
  INT_PROP_1 NUMBER(10,0) DEFAULT NULL,
  INT_PROP_2 NUMBER(10,0) DEFAULT NULL,
  LONG_PROP_1 NUMBER(19,0) DEFAULT NULL,
  LONG_PROP_2 NUMBER(19,0) DEFAULT NULL,
  DEC_PROP_1 NUMBER(13,4) DEFAULT NULL,
  DEC_PROP_2 NUMBER(13,4) DEFAULT NULL,
  BOOL_PROP_1 VARCHAR2(1) DEFAULT NULL,
  BOOL_PROP_2 VARCHAR2(1) DEFAULT NULL,
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_schedule_triggers (
  SCHED_NAME VARCHAR2(120) NOT NULL,
  TRIGGER_NAME VARCHAR2(200) NOT NULL,
  TRIGGER_GROUP VARCHAR2(200) NOT NULL,
  JOB_NAME VARCHAR2(200) NOT NULL,
  JOB_GROUP VARCHAR2(200) NOT NULL,
  DESCRIPTION VARCHAR2(250) DEFAULT NULL,
  NEXT_FIRE_TIME NUMBER(19,0) DEFAULT NULL,
  PREV_FIRE_TIME NUMBER(19,0) DEFAULT NULL,
  PRIORITY NUMBER(10,0) DEFAULT NULL,
  TRIGGER_STATE VARCHAR2(16) NOT NULL,
  TRIGGER_TYPE VARCHAR2(8) NOT NULL,
  START_TIME NUMBER(19,0) NOT NULL,
  END_TIME NUMBER(19,0) DEFAULT NULL,
  CALENDAR_NAME VARCHAR2(200) DEFAULT NULL,
  MISFIRE_INSTR NUMBER(5,0) DEFAULT NULL,
  JOB_DATA BLOB,
  PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE core_reference_area (
  id VARCHAR2(255) NOT NULL,
  "LEVEL" VARCHAR2(255) DEFAULT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  pid VARCHAR2(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_api_traffic_limit (
  id NUMBER(19,0) NOT NULL,
  api VARCHAR2(255) NOT NULL,
  threshold NUMBER(10,0) NOT NULL DEFAULT 2,
  alive NUMBER(10,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_reference_custom_area (
  id VARCHAR2(255) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  pid VARCHAR2(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_chart_view (
  id NUMBER(19,0) NOT NULL,
  title VARCHAR2(1024) DEFAULT NULL,
  scene_id NUMBER(19,0) NOT NULL,
  table_id NUMBER(19,0) DEFAULT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  render VARCHAR2(50) DEFAULT NULL,
  result_count NUMBER(10,0) DEFAULT NULL,
  result_mode VARCHAR2(50) DEFAULT NULL,
  x_axis CLOB,
  x_axis_ext CLOB,
  y_axis CLOB,
  y_axis_ext CLOB,
  ext_stack CLOB,
  ext_bubble CLOB,
  ext_label CLOB,
  ext_tooltip CLOB,
  custom_attr CLOB,
  custom_attr_mobile CLOB,
  custom_style CLOB,
  custom_style_mobile CLOB,
  custom_filter CLOB,
  drill_fields CLOB,
  senior CLOB,
  create_by VARCHAR2(50) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  snapshot CLOB,
  style_priority VARCHAR2(255) DEFAULT 'panel',
  chart_type VARCHAR2(255) DEFAULT 'private',
  is_plugin NUMBER(1,0) DEFAULT NULL,
  data_from VARCHAR2(255) DEFAULT 'dataset',
  view_fields CLOB,
  refresh_view_enable NUMBER(3,0) DEFAULT 0,
  refresh_unit VARCHAR2(255) DEFAULT 'minute',
  refresh_time NUMBER(10,0) DEFAULT 5,
  linkage_active NUMBER(3,0) DEFAULT 0,
  jump_active NUMBER(3,0) DEFAULT 0,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  aggregate NUMBER(1,0) DEFAULT NULL,
  flow_map_start_name CLOB,
  flow_map_end_name CLOB,
  ext_color CLOB,
  sort_priority CLOB,
  PRIMARY KEY (id)
);

CREATE TABLE core_query_assistant_config (
  id NUMBER(19,0) NOT NULL,
  copilot_url VARCHAR2(255) DEFAULT NULL,
  username VARCHAR2(255) DEFAULT NULL,
  pwd VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_query_assistant_message (
  id NUMBER(19,0) NOT NULL,
  user_id NUMBER(19,0) DEFAULT NULL,
  dataset_group_id NUMBER(19,0) DEFAULT NULL,
  msg_type VARCHAR2(255) DEFAULT NULL,
  engine_type VARCHAR2(255) DEFAULT NULL,
  schema_sql CLOB,
  question CLOB,
  history CLOB,
  copilot_sql CLOB,
  api_msg CLOB,
  sql_ok NUMBER(10,0) DEFAULT NULL,
  chart_ok NUMBER(10,0) DEFAULT NULL,
  chart CLOB,
  chart_data CLOB,
  exec_sql CLOB,
  msg_status NUMBER(10,0) DEFAULT NULL,
  err_msg CLOB,
  create_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_query_assistant_token (
  id NUMBER(19,0) NOT NULL,
  type VARCHAR2(255) DEFAULT NULL,
  token CLOB,
  update_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_geo_area (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(50) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_geo_sub_area (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(50) NOT NULL,
  scope VARCHAR2(1024) DEFAULT NULL,
  geo_area_id VARCHAR2(50) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_dataset (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(128) DEFAULT NULL,
  pid NUMBER(19,0) DEFAULT NULL,
  "LEVEL" NUMBER(10,0) DEFAULT 0,
  node_type VARCHAR2(50) NOT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  "MODE" NUMBER(10,0) DEFAULT 0,
  info CLOB,
  create_by VARCHAR2(50) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  scheduler_fire_instance_id VARCHAR2(1024) DEFAULT NULL,
  sync_status VARCHAR2(45) DEFAULT NULL,
  update_by VARCHAR2(50) DEFAULT NULL,
  last_update_time NUMBER(19,0) DEFAULT 0,
  union_sql CLOB,
  is_cross NUMBER(1,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_dataset_sync_task (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  dataset_group_id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  update_type VARCHAR2(50) DEFAULT 'all_scope',
  incremental_field_id NUMBER(19,0) DEFAULT NULL,
  incremental_last_value VARCHAR2(255) DEFAULT NULL,
  start_time NUMBER(19,0) DEFAULT NULL,
  sync_rate VARCHAR2(50) DEFAULT 'RIGHTNOW',
  cron VARCHAR2(255) DEFAULT NULL,
  simple_cron_value NUMBER(19,0) DEFAULT NULL,
  simple_cron_type VARCHAR2(50) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  last_exec_time NUMBER(19,0) DEFAULT NULL,
  last_exec_status VARCHAR2(50) DEFAULT NULL,
  task_status VARCHAR2(50) DEFAULT 'WaitingForExecution',
  cache_ready NUMBER(3,0) DEFAULT 0,
  heartbeat_time NUMBER(19,0) DEFAULT NULL,
  schema_hash VARCHAR2(128) DEFAULT NULL,
  full_sync_interval_hours NUMBER(10,0) DEFAULT 24,
  last_full_sync_time NUMBER(19,0) DEFAULT NULL,
  verify_enabled NUMBER(3,0) DEFAULT 1,
  last_verify_time NUMBER(19,0) DEFAULT NULL,
  last_verify_status VARCHAR2(50) DEFAULT NULL,
  last_verify_message CLOB,
  last_source_row_count NUMBER(19,0) DEFAULT NULL,
  last_cache_row_count NUMBER(19,0) DEFAULT NULL,
  cache_expire_hours NUMBER(10,0) DEFAULT 26,
  task_timeout_minutes NUMBER(10,0) DEFAULT 360,
  consecutive_failures NUMBER(10,0) DEFAULT 0,
  failure_warn_threshold NUMBER(10,0) DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT idx_dataset_sync_task_dataset UNIQUE (dataset_group_id)
);

CREATE TABLE core_dataset_sync_task_log (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  dataset_group_id NUMBER(19,0) NOT NULL,
  task_id NUMBER(19,0) DEFAULT NULL,
  update_type VARCHAR2(50) DEFAULT NULL,
  table_name VARCHAR2(255) DEFAULT NULL,
  start_time NUMBER(19,0) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  task_status VARCHAR2(50) DEFAULT NULL,
  row_count NUMBER(19,0) DEFAULT 0,
  info CLOB,
  create_time NUMBER(19,0) DEFAULT NULL,
  trigger_type VARCHAR2(50) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_asset_profile (
  id NUMBER(19,0) NOT NULL,
  asset_type VARCHAR2(32) NOT NULL,
  asset_id VARCHAR2(128) NOT NULL,
  description VARCHAR2(1024) DEFAULT NULL,
  owner_id NUMBER(19,0) DEFAULT NULL,
  certified NUMBER(3,0) NOT NULL DEFAULT 0,
  recommended NUMBER(3,0) NOT NULL DEFAULT 0,
  deprecated NUMBER(3,0) NOT NULL DEFAULT 0,
  tags VARCHAR2(1024) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_by NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_data_asset_profile_asset UNIQUE (asset_type, asset_id)
);

CREATE TABLE core_dataset_table (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(128) DEFAULT NULL,
  table_name VARCHAR2(128) DEFAULT NULL,
  datasource_id NUMBER(19,0) DEFAULT NULL,
  dataset_group_id NUMBER(19,0) NOT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  info CLOB,
  sql_variable_details CLOB,
  PRIMARY KEY (id)
);

CREATE TABLE core_dataset_field (
  id NUMBER(19,0) NOT NULL,
  datasource_id NUMBER(19,0) DEFAULT NULL,
  dataset_table_id NUMBER(19,0) DEFAULT NULL,
  dataset_group_id NUMBER(19,0) DEFAULT NULL,
  chart_id NUMBER(19,0) DEFAULT NULL,
  origin_name VARCHAR2(1024) NOT NULL,
  name VARCHAR2(1024),
  description VARCHAR2(2048),
  engine_field_name VARCHAR2(255) DEFAULT NULL,
  field_short_name VARCHAR2(255) DEFAULT NULL,
  group_list CLOB,
  other_group CLOB,
  group_type VARCHAR2(50) DEFAULT NULL,
  type VARCHAR2(255) NOT NULL,
  "SIZE" NUMBER(10,0) DEFAULT NULL,
  field_type NUMBER(10,0) NOT NULL,
  extracted_field_type NUMBER(10,0) NOT NULL,
  ext_field NUMBER(10,0) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT 1,
  column_index NUMBER(10,0) DEFAULT NULL,
  last_sync_time NUMBER(19,0) DEFAULT NULL,
  accuracy NUMBER(10,0) DEFAULT 0,
  date_format VARCHAR2(255) DEFAULT NULL,
  date_format_type VARCHAR2(255) DEFAULT NULL,
  params CLOB,
  order_checked NUMBER(3,0) DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_dataset_sql_log (
  id VARCHAR2(50) NOT NULL DEFAULT '',
  table_id VARCHAR2(50) NOT NULL DEFAULT '',
  start_time NUMBER(19,0) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  spend NUMBER(19,0) DEFAULT NULL,
  sql CLOB NOT NULL,
  status VARCHAR2(45) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  name VARCHAR2(255) NOT NULL,
  description VARCHAR2(255) DEFAULT NULL,
  type VARCHAR2(50) NOT NULL,
  pid NUMBER(19,0) DEFAULT NULL,
  edit_type VARCHAR2(50) DEFAULT NULL,
  configuration CLOB,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  update_by NUMBER(19,0) DEFAULT NULL,
  create_by VARCHAR2(50) DEFAULT NULL,
  status CLOB,
  scheduler_fire_instance_id CLOB,
  task_status VARCHAR2(50) DEFAULT NULL,
  enable_data_fill NUMBER(3,0) DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_sync_task (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  ds_id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  update_type VARCHAR2(50) NOT NULL,
  start_time NUMBER(19,0) DEFAULT NULL,
  sync_rate VARCHAR2(50) NOT NULL,
  cron VARCHAR2(255) DEFAULT NULL,
  simple_cron_value NUMBER(19,0) DEFAULT NULL,
  simple_cron_type VARCHAR2(50) DEFAULT NULL,
  end_limit VARCHAR2(50) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  last_exec_time NUMBER(19,0) DEFAULT NULL,
  last_exec_status VARCHAR2(50) DEFAULT NULL,
  extra_data CLOB,
  task_status VARCHAR2(50) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_sync_task_log (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  ds_id NUMBER(19,0) NOT NULL,
  task_id NUMBER(19,0) DEFAULT NULL,
  start_time NUMBER(19,0) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  task_status VARCHAR2(50) NOT NULL,
  table_name VARCHAR2(255) NOT NULL,
  info CLOB,
  create_time NUMBER(19,0) DEFAULT NULL,
  trigger_type VARCHAR2(45) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_engine (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  name VARCHAR2(50) DEFAULT NULL,
  description VARCHAR2(50) DEFAULT NULL,
  type VARCHAR2(50) NOT NULL,
  configuration CLOB,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  create_by VARCHAR2(50) DEFAULT NULL,
  status VARCHAR2(45) DEFAULT NULL,
  enable_data_fill NUMBER(3,0) DEFAULT 1,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_driver (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  name VARCHAR2(50) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  type VARCHAR2(255) DEFAULT NULL,
  driver_class VARCHAR2(255) DEFAULT NULL,
  description VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_driver_jar (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  driver_id VARCHAR2(50) NOT NULL,
  file_name VARCHAR2(255) DEFAULT NULL,
  version VARCHAR2(255) DEFAULT NULL,
  driver_class CLOB,
  trans_name VARCHAR2(255) DEFAULT NULL,
  is_trans_name NUMBER(3,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_datasource_finish_page (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_export_download_task (
  id VARCHAR2(255) NOT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  valid_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_export_task (
  id VARCHAR2(255) NOT NULL,
  user_id NUMBER(19,0) NOT NULL,
  file_name VARCHAR2(2048) DEFAULT NULL,
  file_size BINARY_DOUBLE DEFAULT NULL,
  file_size_unit VARCHAR2(255) DEFAULT NULL,
  export_from VARCHAR2(255) DEFAULT NULL,
  export_status VARCHAR2(255) DEFAULT NULL,
  export_from_type VARCHAR2(255) DEFAULT NULL,
  export_time NUMBER(19,0) DEFAULT NULL,
  export_progress VARCHAR2(255) DEFAULT NULL,
  export_machine_name VARCHAR2(512) DEFAULT NULL,
  params CLOB NOT NULL,
  msg CLOB,
  PRIMARY KEY (id)
);

CREATE TABLE core_font_asset (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  file_name VARCHAR2(255) DEFAULT NULL,
  file_trans_name VARCHAR2(255) DEFAULT NULL,
  is_default NUMBER(3,0) DEFAULT 0,
  update_time NUMBER(19,0) NOT NULL,
  is_BuiltIn NUMBER(3,0) DEFAULT 0,
  "SIZE" BINARY_DOUBLE DEFAULT NULL,
  size_type VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_iam_menu (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  pid NUMBER(19,0) NOT NULL,
  type NUMBER(10,0) DEFAULT NULL,
  name VARCHAR2(45) DEFAULT NULL,
  component VARCHAR2(45) DEFAULT NULL,
  menu_sort NUMBER(10,0) DEFAULT NULL,
  icon VARCHAR2(45) DEFAULT NULL,
  path VARCHAR2(45) DEFAULT NULL,
  hidden NUMBER(3,0) NOT NULL DEFAULT 0,
  in_layout NUMBER(3,0) NOT NULL DEFAULT 1,
  auth NUMBER(3,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_workspace_recent_resource (
  id NUMBER(19,0) NOT NULL,
  resource_id NUMBER(19,0) DEFAULT NULL,
  resource_name VARCHAR2(255) DEFAULT NULL,
  "UID" NUMBER(19,0) NOT NULL,
  resource_type NUMBER(10,0) NOT NULL,
  opt_type NUMBER(10,0) DEFAULT NULL,
  time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_auth_token (
  id NUMBER(10,0) NOT NULL,
  token VARCHAR2(255) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  exp_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_plugin_registry (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  icon CLOB NOT NULL,
  version VARCHAR2(255) NOT NULL,
  install_time NUMBER(19,0) NOT NULL,
  flag VARCHAR2(255) NOT NULL,
  developer VARCHAR2(255) NOT NULL,
  config CLOB NOT NULL,
  require_version VARCHAR2(255) NOT NULL,
  module_name VARCHAR2(255) NOT NULL,
  jar_name VARCHAR2(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_crypto_key (
  id NUMBER(10,0) NOT NULL,
  private_key CLOB NOT NULL,
  public_key CLOB NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  aes_key CLOB NOT NULL,
  sm2_private_key CLOB DEFAULT NULL,
  sm2_public_key CLOB DEFAULT NULL,
  sm2_create_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_auth_setting (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(100) NOT NULL,
  type VARCHAR2(10) NOT NULL,
  enable NUMBER(3,0) NOT NULL,
  sync_time NUMBER(19,0) NOT NULL,
  relational_ids VARCHAR2(255) DEFAULT NULL,
  plugin_json CLOB,
  synced NUMBER(3,0) NOT NULL DEFAULT 0,
  valid NUMBER(3,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_share_link (
  id NUMBER(19,0) NOT NULL,
  creator NUMBER(19,0) NOT NULL,
  time NUMBER(19,0) NOT NULL,
  exp NUMBER(19,0) DEFAULT NULL,
  uuid VARCHAR2(16) NOT NULL,
  pwd VARCHAR2(255) DEFAULT NULL,
  resource_id NUMBER(19,0) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  type NUMBER(10,0) NOT NULL,
  auto_pwd NUMBER(3,0) NOT NULL DEFAULT 1,
  ticket_require NUMBER(3,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_share_ticket (
  id NUMBER(19,0) NOT NULL,
  uuid VARCHAR2(255) NOT NULL,
  ticket VARCHAR2(255) NOT NULL,
  exp NUMBER(19,0) DEFAULT NULL,
  args CLOB,
  access_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_workspace_favorite_resource (
  id NUMBER(19,0) NOT NULL,
  resource_id NUMBER(19,0) NOT NULL,
  "UID" NUMBER(19,0) NOT NULL,
  resource_type NUMBER(10,0) NOT NULL,
  time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_system_setting (
  id NUMBER(19,0) NOT NULL,
  pkey VARCHAR2(255) NOT NULL,
  pval CLOB,
  type VARCHAR2(255) NOT NULL,
  sort NUMBER(10,0) NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE core_system_startup_job (
  id VARCHAR2(64) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  status VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_alert_rule (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  enable NUMBER(3,0) NOT NULL,
  rate_type NUMBER(10,0) NOT NULL,
  rate_value VARCHAR2(255) NOT NULL,
  resource_type VARCHAR2(50) NOT NULL,
  resource_id NUMBER(19,0) NOT NULL,
  chart_type VARCHAR2(255) NOT NULL,
  chart_id NUMBER(19,0) NOT NULL,
  threshold_rules CLOB,
  recisetting VARCHAR2(50) NOT NULL DEFAULT 0,
  reci_users CLOB,
  reci_roles CLOB,
  reci_emails CLOB,
  reci_lark_groups CLOB,
  reci_larksuite_groups CLOB,
  reci_webhooks CLOB,
  msg_title VARCHAR2(255) NOT NULL,
  msg_type NUMBER(10,0) NOT NULL DEFAULT 0,
  msg_content CLOB,
  repeat_send NUMBER(3,0) NOT NULL DEFAULT 1,
  show_field_value NUMBER(3,0) NOT NULL DEFAULT 0,
  status NUMBER(3,0) NOT NULL DEFAULT 0,
  creator NUMBER(19,0) NOT NULL,
  creator_name VARCHAR2(255) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_alert_instance (
  id NUMBER(19,0) NOT NULL,
  task_id NUMBER(19,0) NOT NULL,
  exec_time NUMBER(19,0) NOT NULL,
  status NUMBER(3,0) NOT NULL DEFAULT 0,
  content CLOB,
  msg CLOB,
  PRIMARY KEY (id)
);

CREATE TABLE core_webhook_config (
  id NUMBER(19,0) NOT NULL,
  name VARCHAR2(255) NOT NULL,
  url VARCHAR2(255) NOT NULL,
  content_type VARCHAR2(255) NOT NULL,
  secret VARCHAR2(255) DEFAULT NULL,
  ssl NUMBER(3,0) NOT NULL DEFAULT 0,
  oid NUMBER(19,0) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_iam_user (
  id NUMBER(19,0) NOT NULL,
  account VARCHAR2(64) NOT NULL,
  name VARCHAR2(128) NOT NULL,
  email VARCHAR2(255) DEFAULT NULL,
  phone_prefix VARCHAR2(16) DEFAULT NULL,
  phone VARCHAR2(64) DEFAULT NULL,
  password_hash VARCHAR2(512) NOT NULL,
  enable NUMBER(3,0) NOT NULL DEFAULT 1,
  is_admin NUMBER(3,0) NOT NULL DEFAULT 0,
  origin NUMBER(10,0) NOT NULL DEFAULT 0,
  auth_type VARCHAR2(32) DEFAULT 'LOCAL' NOT NULL,
  external_id VARCHAR2(128) DEFAULT NULL,
  last_login_time NUMBER(19,0) DEFAULT NULL,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_user_account UNIQUE (account)
);

CREATE TABLE core_visualization (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  pid VARCHAR2(50) DEFAULT NULL,
  org_id VARCHAR2(50) DEFAULT NULL,
  "LEVEL" NUMBER(10,0) DEFAULT NULL,
  node_type VARCHAR2(255) DEFAULT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  canvas_style_data CLOB,
  component_data CLOB,
  mobile_layout NUMBER(3,0) DEFAULT 0,
  status NUMBER(10,0) DEFAULT 1,
  self_watermark_status NUMBER(10,0) DEFAULT 0,
  sort NUMBER(10,0) DEFAULT 0,
  create_time NUMBER(19,0) DEFAULT NULL,
  create_by VARCHAR2(255) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_by VARCHAR2(255) DEFAULT NULL,
  remark VARCHAR2(255) DEFAULT NULL,
  source VARCHAR2(255) DEFAULT NULL,
  delete_flag NUMBER(3,0) DEFAULT 0,
  delete_time NUMBER(19,0) DEFAULT NULL,
  delete_by VARCHAR2(255) DEFAULT NULL,
  version NUMBER(10,0) DEFAULT 3,
  content_id VARCHAR2(50) DEFAULT 0,
  check_version VARCHAR2(50) DEFAULT 1,
  PRIMARY KEY (id)
);

CREATE TABLE core_template_init_history (
  installed_rank NUMBER(10,0) NOT NULL,
  version VARCHAR2(50) DEFAULT NULL,
  description VARCHAR2(200) DEFAULT NULL,
  type VARCHAR2(20) DEFAULT NULL,
  script VARCHAR2(1000) NOT NULL,
  checksum NUMBER(10,0) DEFAULT NULL,
  installed_by VARCHAR2(100) DEFAULT NULL,
  installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  execution_time NUMBER(10,0) DEFAULT NULL,
  success NUMBER(3,0) NOT NULL,
  PRIMARY KEY (installed_rank)
);

CREATE TABLE core_chart_view_snapshot (
  id NUMBER(19,0) NOT NULL,
  title VARCHAR2(1024) DEFAULT NULL,
  scene_id NUMBER(19,0) NOT NULL,
  table_id NUMBER(19,0) DEFAULT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  render VARCHAR2(50) DEFAULT NULL,
  result_count NUMBER(10,0) DEFAULT NULL,
  result_mode VARCHAR2(50) DEFAULT NULL,
  x_axis CLOB,
  x_axis_ext CLOB,
  y_axis CLOB,
  y_axis_ext CLOB,
  ext_stack CLOB,
  ext_bubble CLOB,
  ext_label CLOB,
  ext_tooltip CLOB,
  custom_attr CLOB,
  custom_attr_mobile CLOB,
  custom_style CLOB,
  custom_style_mobile CLOB,
  custom_filter CLOB,
  drill_fields CLOB,
  senior CLOB,
  create_by VARCHAR2(50) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  snapshot CLOB,
  style_priority VARCHAR2(255) DEFAULT 'panel',
  chart_type VARCHAR2(255) DEFAULT 'private',
  is_plugin NUMBER(1,0) DEFAULT NULL,
  data_from VARCHAR2(255) DEFAULT 'dataset',
  view_fields CLOB,
  refresh_view_enable NUMBER(3,0) DEFAULT 0,
  refresh_unit VARCHAR2(255) DEFAULT 'minute',
  refresh_time NUMBER(10,0) DEFAULT 5,
  linkage_active NUMBER(3,0) DEFAULT 0,
  jump_active NUMBER(3,0) DEFAULT 0,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  aggregate NUMBER(1,0) DEFAULT NULL,
  flow_map_start_name CLOB,
  flow_map_end_name CLOB,
  ext_color CLOB,
  sort_priority CLOB,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_snapshot (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  pid VARCHAR2(50) DEFAULT NULL,
  org_id VARCHAR2(50) DEFAULT NULL,
  "LEVEL" NUMBER(10,0) DEFAULT NULL,
  node_type VARCHAR2(255) DEFAULT NULL,
  type VARCHAR2(50) DEFAULT NULL,
  canvas_style_data CLOB,
  component_data CLOB,
  mobile_layout NUMBER(3,0) DEFAULT 0,
  status NUMBER(10,0) DEFAULT 1,
  self_watermark_status NUMBER(10,0) DEFAULT 0,
  sort NUMBER(10,0) DEFAULT 0,
  create_time NUMBER(19,0) DEFAULT NULL,
  create_by VARCHAR2(255) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_by VARCHAR2(255) DEFAULT NULL,
  remark VARCHAR2(255) DEFAULT NULL,
  source VARCHAR2(255) DEFAULT NULL,
  delete_flag NUMBER(3,0) DEFAULT 0,
  delete_time NUMBER(19,0) DEFAULT NULL,
  delete_by VARCHAR2(255) DEFAULT NULL,
  version NUMBER(10,0) DEFAULT 3,
  content_id VARCHAR2(50) DEFAULT 0,
  check_version VARCHAR2(50) DEFAULT 1,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump_snapshot (
  id NUMBER(19,0) NOT NULL,
  source_dv_id NUMBER(19,0) DEFAULT NULL,
  source_view_id NUMBER(19,0) DEFAULT NULL,
  link_jump_info VARCHAR2(4000) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump_action_snapshot (
  id NUMBER(19,0) NOT NULL,
  link_jump_id NUMBER(19,0) DEFAULT NULL,
  link_type VARCHAR2(255) DEFAULT NULL,
  jump_type VARCHAR2(255) DEFAULT NULL,
  target_dv_id NUMBER(19,0) DEFAULT NULL,
  source_field_id NUMBER(19,0) DEFAULT NULL,
  content VARCHAR2(4000) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  attach_params NUMBER(3,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  window_size VARCHAR2(255) DEFAULT 'middle',
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump_target_snapshot (
  target_id NUMBER(19,0) NOT NULL,
  link_jump_info_id NUMBER(19,0) DEFAULT NULL,
  source_field_active_id NUMBER(19,0) DEFAULT NULL,
  target_view_id VARCHAR2(50) DEFAULT NULL,
  target_field_id VARCHAR2(50) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  target_type VARCHAR2(50) DEFAULT 'view',
  PRIMARY KEY (target_id)
);

CREATE TABLE core_visualization_linkage_snapshot (
  id NUMBER(19,0) NOT NULL,
  dv_id NUMBER(19,0) DEFAULT NULL,
  source_view_id NUMBER(19,0) DEFAULT NULL,
  target_view_id NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_people VARCHAR2(255) DEFAULT NULL,
  linkage_active NUMBER(3,0) DEFAULT 0,
  ext1 VARCHAR2(2000) DEFAULT NULL,
  ext2 VARCHAR2(2000) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_linkage_field_snapshot (
  id NUMBER(19,0) NOT NULL,
  linkage_id NUMBER(19,0) DEFAULT NULL,
  source_field NUMBER(19,0) DEFAULT NULL,
  target_field NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_parameter_snapshot (
  params_id VARCHAR2(50) NOT NULL,
  visualization_id VARCHAR2(50) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  remark VARCHAR2(255) DEFAULT NULL,
  copy_from VARCHAR2(50) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  PRIMARY KEY (params_id)
);

CREATE TABLE core_visualization_parameter_item_snapshot (
  params_info_id VARCHAR2(50) NOT NULL,
  params_id VARCHAR2(50) DEFAULT NULL,
  param_name VARCHAR2(255) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  copy_from VARCHAR2(255) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  required NUMBER(3,0) DEFAULT 0,
  default_value VARCHAR2(255) DEFAULT NULL,
  enabled_default NUMBER(3,0) DEFAULT 0,
  PRIMARY KEY (params_info_id)
);

CREATE TABLE core_visualization_parameter_target_snapshot (
  target_id VARCHAR2(50) NOT NULL,
  params_info_id VARCHAR2(50) DEFAULT NULL,
  target_view_id VARCHAR2(50) DEFAULT NULL,
  target_field_id VARCHAR2(50) DEFAULT NULL,
  copy_from VARCHAR2(255) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  target_ds_id VARCHAR2(50) DEFAULT NULL,
  match_mode VARCHAR2(255) DEFAULT 'self',
  PRIMARY KEY (target_id)
);

CREATE TABLE core_visualization_background (
  id VARCHAR2(64) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  classification VARCHAR2(255) NOT NULL,
  content CLOB,
  remark VARCHAR2(255) DEFAULT NULL,
  sort NUMBER(10,0) DEFAULT NULL,
  upload_time NUMBER(19,0) DEFAULT NULL,
  base_url VARCHAR2(255) DEFAULT NULL,
  url VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_background_image (
  id VARCHAR2(64) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  classification VARCHAR2(255) NOT NULL,
  content CLOB,
  remark VARCHAR2(255) DEFAULT NULL,
  sort NUMBER(10,0) DEFAULT NULL,
  upload_time NUMBER(19,0) DEFAULT NULL,
  base_url VARCHAR2(255) DEFAULT NULL,
  url VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump (
  id NUMBER(19,0) NOT NULL,
  source_dv_id NUMBER(19,0) DEFAULT NULL,
  source_view_id NUMBER(19,0) DEFAULT NULL,
  link_jump_info VARCHAR2(4000) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump_action (
  id NUMBER(19,0) NOT NULL,
  link_jump_id NUMBER(19,0) DEFAULT NULL,
  link_type VARCHAR2(255) DEFAULT NULL,
  jump_type VARCHAR2(255) DEFAULT NULL,
  target_dv_id NUMBER(19,0) DEFAULT NULL,
  source_field_id NUMBER(19,0) DEFAULT NULL,
  content VARCHAR2(4000) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  attach_params NUMBER(3,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  window_size VARCHAR2(255) DEFAULT 'middle',
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_jump_target (
  target_id NUMBER(19,0) NOT NULL,
  link_jump_info_id NUMBER(19,0) DEFAULT NULL,
  source_field_active_id NUMBER(19,0) DEFAULT NULL,
  target_view_id VARCHAR2(50) DEFAULT NULL,
  target_field_id VARCHAR2(50) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  target_type VARCHAR2(50) DEFAULT 'view',
  PRIMARY KEY (target_id)
);

CREATE TABLE core_visualization_linkage (
  id NUMBER(19,0) NOT NULL,
  dv_id NUMBER(19,0) DEFAULT NULL,
  source_view_id NUMBER(19,0) DEFAULT NULL,
  target_view_id NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_people VARCHAR2(255) DEFAULT NULL,
  linkage_active NUMBER(3,0) DEFAULT 0,
  ext1 VARCHAR2(2000) DEFAULT NULL,
  ext2 VARCHAR2(2000) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_linkage_field (
  id NUMBER(19,0) NOT NULL,
  linkage_id NUMBER(19,0) DEFAULT NULL,
  source_field NUMBER(19,0) DEFAULT NULL,
  target_field NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  copy_from NUMBER(19,0) DEFAULT NULL,
  copy_id NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_parameter (
  params_id VARCHAR2(50) NOT NULL,
  visualization_id VARCHAR2(50) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  remark VARCHAR2(255) DEFAULT NULL,
  copy_from VARCHAR2(50) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  PRIMARY KEY (params_id)
);

CREATE TABLE core_visualization_parameter_item (
  params_info_id VARCHAR2(50) NOT NULL,
  params_id VARCHAR2(50) DEFAULT NULL,
  param_name VARCHAR2(255) DEFAULT NULL,
  checked NUMBER(3,0) DEFAULT NULL,
  copy_from VARCHAR2(255) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  required NUMBER(3,0) DEFAULT 0,
  default_value VARCHAR2(255) DEFAULT NULL,
  enabled_default NUMBER(3,0) DEFAULT 0,
  PRIMARY KEY (params_info_id)
);

CREATE TABLE core_visualization_parameter_target (
  target_id VARCHAR2(50) NOT NULL,
  params_info_id VARCHAR2(50) DEFAULT NULL,
  target_view_id VARCHAR2(50) DEFAULT NULL,
  target_field_id VARCHAR2(50) DEFAULT NULL,
  copy_from VARCHAR2(255) DEFAULT NULL,
  copy_id VARCHAR2(50) DEFAULT NULL,
  target_ds_id VARCHAR2(50) DEFAULT NULL,
  match_mode VARCHAR2(255) DEFAULT 'self',
  PRIMARY KEY (target_id)
);

CREATE TABLE core_visualization_report_filter (
  id NUMBER(19,0) NOT NULL,
  report_id NUMBER(19,0) DEFAULT NULL,
  task_id NUMBER(19,0) DEFAULT NULL,
  resource_id NUMBER(19,0) DEFAULT NULL,
  dv_type VARCHAR2(255) DEFAULT NULL,
  component_id NUMBER(19,0) DEFAULT NULL,
  filter_id NUMBER(19,0) DEFAULT NULL,
  filter_info CLOB,
  filter_version NUMBER(10,0) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  create_user VARCHAR2(255) DEFAULT NULL
);

CREATE TABLE core_visualization_theme (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  type VARCHAR2(255) DEFAULT NULL,
  details CLOB,
  delete_flag NUMBER(3,0) DEFAULT 0,
  cover_url CLOB,
  create_num NUMBER(10,0) NOT NULL DEFAULT 0,
  create_time NUMBER(19,0) DEFAULT NULL,
  create_by VARCHAR2(255) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  update_by VARCHAR2(255) DEFAULT NULL,
  delete_time NUMBER(19,0) DEFAULT NULL,
  delete_by NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_template (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  pid VARCHAR2(255) DEFAULT NULL,
  "LEVEL" NUMBER(10,0) DEFAULT NULL,
  dv_type VARCHAR2(255) DEFAULT NULL,
  node_type VARCHAR2(255) DEFAULT NULL,
  create_by VARCHAR2(255) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  snapshot CLOB,
  template_type VARCHAR2(255) DEFAULT NULL,
  template_style CLOB,
  template_data CLOB,
  dynamic_data CLOB,
  app_data CLOB,
  use_count NUMBER(10,0) DEFAULT 0,
  version NUMBER(10,0) DEFAULT 3,
  PRIMARY KEY (id)
);

CREATE TABLE core_template_category (
  id VARCHAR2(50) NOT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  pid VARCHAR2(255) DEFAULT NULL,
  "LEVEL" NUMBER(10,0) DEFAULT NULL,
  dv_type VARCHAR2(255) DEFAULT NULL,
  node_type VARCHAR2(255) DEFAULT NULL,
  create_by VARCHAR2(255) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  snapshot CLOB,
  template_type VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_template_category_relation (
  id VARCHAR2(50) NOT NULL,
  category_id VARCHAR2(255) DEFAULT NULL,
  template_id VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_template_view_data (
  id NUMBER(19,0) NOT NULL,
  dv_id NUMBER(19,0) DEFAULT NULL,
  view_id NUMBER(19,0) DEFAULT NULL,
  view_details CLOB,
  copy_from VARCHAR2(255) DEFAULT NULL,
  copy_id VARCHAR2(255) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_visualization_watermark (
  id VARCHAR2(50) NOT NULL,
  version VARCHAR2(255) DEFAULT NULL,
  setting_content CLOB,
  create_by VARCHAR2(255) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE core_auth_sso_provider (
  id NUMBER(19,0) NOT NULL,
  provider_key VARCHAR2(64) NOT NULL,
  provider_type VARCHAR2(32) NOT NULL,
  name VARCHAR2(128) NOT NULL,
  enabled NUMBER(3,0) NOT NULL DEFAULT 1,
  client_id VARCHAR2(191) DEFAULT NULL,
  client_secret CLOB DEFAULT NULL,
  authorization_endpoint CLOB DEFAULT NULL,
  token_endpoint CLOB DEFAULT NULL,
  user_info_endpoint CLOB DEFAULT NULL,
  issuer CLOB DEFAULT NULL,
  scope VARCHAR2(255) DEFAULT NULL,
  redirect_uri CLOB DEFAULT NULL,
  user_id_attribute VARCHAR2(128) DEFAULT NULL,
  account_attribute VARCHAR2(128) DEFAULT NULL,
  name_attribute VARCHAR2(128) DEFAULT NULL,
  email_attribute VARCHAR2(128) DEFAULT NULL,
  union_id_attribute VARCHAR2(128) DEFAULT NULL,
  auto_create_user NUMBER(3,0) DEFAULT 1 NOT NULL,
  require_https NUMBER(3,0) DEFAULT 1 NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_sso_provider_key UNIQUE (provider_key)
);

CREATE TABLE core_auth_sso_identity_binding (
  id NUMBER(19,0) NOT NULL,
  user_id NUMBER(19,0) NOT NULL,
  provider_id NUMBER(19,0) NOT NULL,
  provider_type VARCHAR2(32) NOT NULL,
  external_subject VARCHAR2(191) NOT NULL,
  account VARCHAR2(64) NOT NULL,
  display_name VARCHAR2(64) NOT NULL,
  email VARCHAR2(191) DEFAULT NULL,
  union_id VARCHAR2(191) DEFAULT NULL,
  last_login_time NUMBER(19,0) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_sso_identity_subject UNIQUE (provider_id, external_subject),
  CONSTRAINT uk_sso_identity_union UNIQUE (provider_id, union_id)
);

CREATE TABLE core_audit_log (
  id NUMBER(19,0) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  operation_type VARCHAR2(50) NOT NULL,
  resource_type VARCHAR2(50) NOT NULL,
  resource_id VARCHAR2(100),
  resource_name VARCHAR2(200),
  operation_desc VARCHAR2(500),
  request_method VARCHAR2(10),
  request_url VARCHAR2(500),
  request_params CLOB,
  response_code NUMBER(10,0),
  response_msg VARCHAR2(500),
  operator_id NUMBER(19,0) NOT NULL,
  operator_name VARCHAR2(100),
  operator_account VARCHAR2(100),
  operator_ip VARCHAR2(50),
  operation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  duration NUMBER(19,0)
);

CREATE TABLE core_iam_org (
  id NUMBER(19,0) NOT NULL,
  pid NUMBER(19,0) DEFAULT NULL,
  name VARCHAR2(128) NOT NULL,
  code VARCHAR2(64) DEFAULT NULL,
  path VARCHAR2(1024) NOT NULL,
  "LEVEL" NUMBER(10,0) NOT NULL DEFAULT 0,
  sort NUMBER(10,0) NOT NULL DEFAULT 0,
  enable NUMBER(3,0) NOT NULL DEFAULT 1,
  readonly NUMBER(3,0) NOT NULL DEFAULT 0,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_org_code UNIQUE (code)
);

CREATE TABLE core_iam_role (
  id NUMBER(19,0) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  name VARCHAR2(128) NOT NULL,
  code VARCHAR2(64) DEFAULT NULL,
  description VARCHAR2(255) DEFAULT NULL,
  type_code NUMBER(10,0) NOT NULL DEFAULT 0,
  readonly NUMBER(3,0) NOT NULL DEFAULT 0,
  system_role NUMBER(3,0) NOT NULL DEFAULT 0,
  org_admin NUMBER(3,0) NOT NULL DEFAULT 0,
  create_time NUMBER(19,0) NOT NULL,
  update_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_role_oid_code UNIQUE (oid, code)
);

CREATE TABLE core_iam_user_org (
  id NUMBER(19,0) NOT NULL,
  "UID" NUMBER(19,0) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  default_org NUMBER(3,0) NOT NULL DEFAULT 0,
  create_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_user_org_uid_oid UNIQUE ("UID", oid)
);

CREATE TABLE core_iam_user_role (
  id NUMBER(19,0) NOT NULL,
  "UID" NUMBER(19,0) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  rid NUMBER(19,0) NOT NULL,
  create_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_user_role_uid_oid_rid UNIQUE ("UID", oid, rid)
);

CREATE TABLE core_iam_role_menu_permission (
  id NUMBER(19,0) NOT NULL,
  rid NUMBER(19,0) NOT NULL,
  menu_id NUMBER(19,0) NOT NULL,
  permission VARCHAR2(32) NOT NULL DEFAULT 'read',
  create_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_role_menu_perm UNIQUE (rid, menu_id, permission)
);

CREATE TABLE core_iam_resource_index (
  id NUMBER(19,0) NOT NULL,
  resource_id VARCHAR2(64) NOT NULL,
  resource_type VARCHAR2(32) NOT NULL,
  oid NUMBER(19,0) NOT NULL,
  creator NUMBER(19,0) DEFAULT NULL,
  name VARCHAR2(255) DEFAULT NULL,
  create_time NUMBER(19,0) DEFAULT NULL,
  update_time NUMBER(19,0) DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_resource_unique UNIQUE (resource_type, resource_id)
);

CREATE TABLE core_iam_resource_permission (
  id NUMBER(19,0) NOT NULL,
  resource_type VARCHAR2(32) NOT NULL,
  resource_id VARCHAR2(64) NOT NULL,
  target_type VARCHAR2(16) NOT NULL,
  target_id NUMBER(19,0) NOT NULL,
  permission VARCHAR2(32) NOT NULL DEFAULT 'read',
  create_time NUMBER(19,0) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_crest_resource_permission UNIQUE (resource_type, resource_id, target_type, target_id, permission)
);

-- Secondary indexes
CREATE INDEX idx_core_schedule_triggers_job ON core_schedule_triggers (SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX idx_dataset_sync_task_log_A ON core_dataset_sync_task_log (dataset_group_id,task_id,start_time);
CREATE INDEX idx_data_asset_profile_status ON core_asset_profile (asset_type, certified, recommended, deprecated);
CREATE INDEX idx_data_asset_profile_owner ON core_asset_profile (owner_id);
CREATE INDEX idx_dataset_table_task_log_ds_id ON core_datasource_sync_task_log (ds_id);
CREATE INDEX idx_dataset_table_task_log_task_id ON core_datasource_sync_task_log (task_id);
CREATE INDEX idx_dataset_table_task_log_A ON core_datasource_sync_task_log (ds_id,table_name,start_time);
CREATE INDEX idx_sso_provider_type ON core_auth_sso_provider (provider_type, enabled);
CREATE INDEX idx_sso_identity_user ON core_auth_sso_identity_binding (user_id, provider_id);
CREATE INDEX idx_sso_identity_account ON core_auth_sso_identity_binding (account);
CREATE INDEX idx_operation_time ON core_audit_log (operation_time);
CREATE INDEX idx_operator_id ON core_audit_log (operator_id);
CREATE INDEX idx_operation_type ON core_audit_log (operation_type);
CREATE INDEX idx_resource_type ON core_audit_log (resource_type);
CREATE INDEX idx_crest_org_pid ON core_iam_org (pid);
CREATE INDEX idx_crest_org_path ON core_iam_org (path);
CREATE INDEX idx_crest_role_oid ON core_iam_role (oid);
CREATE INDEX idx_crest_user_org_oid ON core_iam_user_org (oid);
CREATE INDEX idx_crest_user_role_rid ON core_iam_user_role (rid);
CREATE INDEX idx_crest_role_menu_id ON core_iam_role_menu_permission (menu_id);
CREATE INDEX idx_crest_resource_oid_type ON core_iam_resource_index (oid, resource_type);
CREATE INDEX idx_crest_resource_creator ON core_iam_resource_index (creator);
CREATE INDEX idx_crest_resource_permission_target ON core_iam_resource_permission (target_type, target_id);

-- Foreign keys
ALTER TABLE core_schedule_blob_triggers ADD CONSTRAINT fk_csch_blob_trigger FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES core_schedule_triggers (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
ALTER TABLE core_schedule_cron_triggers ADD CONSTRAINT fk_csch_cron_trigger FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES core_schedule_triggers (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
ALTER TABLE core_schedule_simple_triggers ADD CONSTRAINT fk_csch_simple_trigger FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES core_schedule_triggers (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
ALTER TABLE core_schedule_simprop_triggers ADD CONSTRAINT fk_csch_simprop_trigger FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES core_schedule_triggers (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
ALTER TABLE core_schedule_triggers ADD CONSTRAINT fk_csch_trigger_job FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES core_schedule_job_details (SCHED_NAME, JOB_NAME, JOB_GROUP);

-- Baseline seed data
-- Seed for core_reference_area is loaded by ObOracleMetadataSeedInitializer.

UPDATE core_asset_profile SET certified = 0, recommended = 0 WHERE deprecated = 1;

INSERT INTO core_datasource_engine VALUES (1,'默认引擎','默认引擎','obOracle','',NULL,NULL,NULL,NULL,1);

INSERT INTO core_font_asset VALUES (1,'PingFang',NULL,NULL,1,0,1,NULL,NULL);

INSERT INTO core_iam_menu VALUES (1,0,2,'workbranch','workbranch',1,NULL,'/workbranch',0,1,1);
INSERT INTO core_iam_menu VALUES (2,0,2,'panel','visualized/view/panel',2,NULL,'/panel',0,1,1);
INSERT INTO core_iam_menu VALUES (3,0,2,'screen','visualized/view/screen',3,NULL,'/screen',0,1,1);
INSERT INTO core_iam_menu VALUES (4,0,1,'data',NULL,5,NULL,'/data',0,1,0);
INSERT INTO core_iam_menu VALUES (5,4,2,'dataset','visualized/data/dataset',1,NULL,'/dataset',0,1,1);
INSERT INTO core_iam_menu VALUES (6,4,2,'datasource','visualized/data/datasource',2,NULL,'/datasource',0,1,1);
INSERT INTO core_iam_menu VALUES (11,0,2,'dataset-form','visualized/data/dataset/form',7,NULL,'/dataset-form',1,0,0);
INSERT INTO core_iam_menu VALUES (12,0,2,'datasource-form','visualized/data/datasource/form',7,NULL,'/ds-form',1,0,0);
INSERT INTO core_iam_menu VALUES (15,0,1,'sys-setting',NULL,6,NULL,'/sys-setting',1,1,0);
INSERT INTO core_iam_menu VALUES (16,15,2,'parameter','system/parameter',1,'sys-parameter','/parameter',0,1,0);
INSERT INTO core_iam_menu VALUES (66,0,2,'association','visualized/data/lineage',4,'association','/lineage',0,1,1);
INSERT INTO core_iam_menu VALUES (67,15,2,'share-management','system/share',2,'icon_share-label_outlined','/share-management',0,1,1);
INSERT INTO core_iam_menu VALUES (68,15,2,'site-setting','system/site',3,'tab-title','/site-setting',0,1,1);
INSERT INTO core_iam_menu VALUES (69,15,2,'user-management','system/user',4,'icon_member_filled','/user-management',0,1,1);
INSERT INTO core_iam_menu VALUES (70,0,1,'msg',NULL,200,NULL,'/msg',1,1,0);


INSERT INTO core_system_setting VALUES (1,'basic.dsIntervalTime','6','text',11);
INSERT INTO core_system_setting VALUES (2,'basic.dsExecuteTime','minute','text',3);
INSERT INTO core_system_setting VALUES (3,'ai.baseUrl','','text',0);
INSERT INTO core_system_setting VALUES (7,'template.url','','text',0);
INSERT INTO core_system_setting VALUES (8,'template.accessKey','crest','text',1);
INSERT INTO core_system_setting VALUES (9,'basic.frontTimeOut','60','text',1);
INSERT INTO core_system_setting VALUES (10,'basic.exportFileLiveTime','30','text',2);
INSERT INTO core_system_setting VALUES (100102206800000001,'basic.siteTitle','Crest','text',9);
INSERT INTO core_system_setting VALUES (1048232869488627717,'basic.shareDisable','false','text',11);
INSERT INTO core_system_setting VALUES (1048232869488627718,'basic.sharePeRequire','false','text',12);
INSERT INTO core_system_setting VALUES (1048232869488627719,'basic.defaultSort','1','text',13);
INSERT INTO core_system_setting VALUES (1048232869488627720,'basic.defaultOpen','0','text',14);
INSERT INTO core_system_setting VALUES (1048232869488627721,'basic.initialPassword','','pwd',15);
INSERT INTO core_system_setting VALUES (1048232869488627722,'basic.pwdStrategy','true','text',16);
INSERT INTO core_system_setting VALUES (1048232869488627723,'basic.dip','false','text',17);
INSERT INTO core_system_setting VALUES (1048232869488627724,'basic.pvp','0','text',18);
INSERT INTO core_system_setting VALUES (1048232869488627725,'basic.loginLimit','false','text',19);
INSERT INTO core_system_setting VALUES (1048232869488627726,'basic.loginLimitRate','5','text',20);
INSERT INTO core_system_setting VALUES (1048232869488627727,'basic.loginLimitTime','30','text',21);

INSERT INTO core_system_startup_job VALUES ('chartFilterDynamic','chartFilterDynamic','done');
INSERT INTO core_system_startup_job VALUES ('chartFilterMerge','chartFilterMerge','done');
INSERT INTO core_system_startup_job VALUES ('datasetCrossListener','datasetCrossListener','done');

INSERT INTO core_iam_user (id, account, name, email, phone_prefix, phone, password_hash, enable, is_admin, origin, create_time, update_time)
VALUES (1,'admin','管理员',NULL,NULL,NULL,'{CREST_INITIAL_PASSWORD_REQUIRED}',1,1,0,1779664240000,1779664240000);

INSERT INTO core_visualization_background VALUES ('board_1','1','default','',NULL,NULL,NULL,'img/board','board/board_1.svg');
INSERT INTO core_visualization_background VALUES ('board_2','2','default',NULL,NULL,NULL,NULL,'img/board','board/board_2.svg');
INSERT INTO core_visualization_background VALUES ('board_3','3','default',NULL,NULL,NULL,NULL,'img/board','board/board_3.svg');
INSERT INTO core_visualization_background VALUES ('board_4','4','default',NULL,NULL,NULL,NULL,'img/board','board/board_4.svg');
INSERT INTO core_visualization_background VALUES ('board_5','5','default',NULL,NULL,NULL,NULL,'img/board','board/board_5.svg');
INSERT INTO core_visualization_background VALUES ('board_6','6','default',NULL,NULL,NULL,NULL,'img/board','board/board_6.svg');
INSERT INTO core_visualization_background VALUES ('board_7','7','default',NULL,NULL,NULL,NULL,'img/board','board/board_7.svg');
INSERT INTO core_visualization_background VALUES ('board_8','8','default',NULL,NULL,NULL,NULL,'img/board','board/board_8.svg');
INSERT INTO core_visualization_background VALUES ('board_9','9','default',NULL,NULL,NULL,NULL,'img/board','board/board_9.svg');

-- Seed for core_visualization_theme is loaded by ObOracleMetadataSeedInitializer.

INSERT INTO core_visualization_watermark VALUES ('system_default','1.0','{"enable":false,"enablePanelCustom":true,"type":"custom","content":"水印","watermark_color":"#DD1010","watermark_x_space":12,"watermark_y_space":36,"watermark_fontsize":15}','admin',NULL);

INSERT INTO core_iam_menu VALUES (71,15,2,'single-sign-on','system/sso',5,'authentication','/single-sign-on',0,1,1);

INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000001,'sso.enabled','false','text',1);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000002,'sso.providerName','企业单点登录','text',2);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000003,'sso.clientId','','text',3);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000004,'sso.clientSecret','','text',4);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000005,'sso.authorizationEndpoint','','text',5);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000006,'sso.tokenEndpoint','','text',6);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000007,'sso.userInfoEndpoint','','text',7);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000008,'sso.issuer','','text',8);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000009,'sso.scope','openid profile email','text',9);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000010,'sso.redirectUri','','text',10);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000011,'sso.userIdAttribute','sub','text',11);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000012,'sso.accountAttribute','preferred_username','text',12);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000013,'sso.nameAttribute','name','text',13);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000014,'sso.emailAttribute','email','text',14);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000015,'sso.autoCreateUser','true','text',15);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000016,'sso.allowLocalLogin','true','text',16);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000017,'sso.requireHttps','true','text',17);
INSERT INTO core_system_setting (id, pkey, pval, type, sort) VALUES (100140000000000018,'sso.logoutRedirectUrl','','text',18);

UPDATE core_iam_user
SET name = '管理员'
WHERE id = 1
  AND (name LIKE '%<%' OR name LIKE '%>%');

UPDATE core_iam_user
SET name = account
WHERE id <> 1
  AND (name LIKE '%<%' OR name LIKE '%>%');

UPDATE core_system_setting
SET pval = '统一身份认证'
WHERE pkey = 'sso.providerName'
  AND DBMS_LOB.SUBSTR(pval, 4000, 1) = '企业单点登录';

INSERT INTO core_auth_sso_provider (id, provider_key, provider_type, name, enabled, create_time, update_time) VALUES (1, 'default', 'OIDC_GENERIC', '统一身份认证', 1, 0, 0);

INSERT INTO core_system_setting (id, pkey, pval, type, sort)
SELECT
    100140000000000019,
    'sso.providerType',
    CASE
        WHEN EXISTS (
            SELECT 1 FROM (
                SELECT pval FROM core_system_setting
                WHERE pkey IN ('sso.providerName', 'sso.issuer')
                  AND LOWER(DBMS_LOB.SUBSTR(pval, 4000, 1)) LIKE '%casdoor%'
            ) existing_provider
        ) THEN 'CASDOOR'
        ELSE 'OIDC_GENERIC'
    END,
    'text',
    19
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM (
        SELECT id FROM core_system_setting WHERE pkey = 'sso.providerType'
    ) existing_setting
);

INSERT INTO core_system_setting (id, pkey, pval, type, sort)
SELECT 100140000000000020, 'sso.unionIdAttribute', '', 'text', 20
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM (
        SELECT id FROM core_system_setting WHERE pkey = 'sso.unionIdAttribute'
    ) existing_setting
);

UPDATE core_auth_sso_provider
SET
    provider_type = COALESCE((SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.providerType' FETCH FIRST 1 ROWS ONLY), provider_type),
    name = COALESCE((SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.providerName' FETCH FIRST 1 ROWS ONLY), name),
    enabled = COALESCE((SELECT CASE WHEN LOWER(DBMS_LOB.SUBSTR(pval, 4000, 1)) = 'true' THEN 1 ELSE 0 END FROM core_system_setting WHERE pkey = 'sso.enabled' FETCH FIRST 1 ROWS ONLY), enabled),
    client_id = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.clientId' FETCH FIRST 1 ROWS ONLY),
    client_secret = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.clientSecret' FETCH FIRST 1 ROWS ONLY),
    authorization_endpoint = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.authorizationEndpoint' FETCH FIRST 1 ROWS ONLY),
    token_endpoint = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.tokenEndpoint' FETCH FIRST 1 ROWS ONLY),
    user_info_endpoint = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.userInfoEndpoint' FETCH FIRST 1 ROWS ONLY),
    issuer = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.issuer' FETCH FIRST 1 ROWS ONLY),
    scope = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.scope' FETCH FIRST 1 ROWS ONLY),
    redirect_uri = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.redirectUri' FETCH FIRST 1 ROWS ONLY),
    user_id_attribute = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.userIdAttribute' FETCH FIRST 1 ROWS ONLY),
    account_attribute = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.accountAttribute' FETCH FIRST 1 ROWS ONLY),
    name_attribute = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.nameAttribute' FETCH FIRST 1 ROWS ONLY),
    email_attribute = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.emailAttribute' FETCH FIRST 1 ROWS ONLY),
    union_id_attribute = (SELECT DBMS_LOB.SUBSTR(pval, 4000, 1) FROM core_system_setting WHERE pkey = 'sso.unionIdAttribute' FETCH FIRST 1 ROWS ONLY),
    auto_create_user = COALESCE((SELECT CASE WHEN LOWER(DBMS_LOB.SUBSTR(pval, 4000, 1)) = 'true' THEN 1 ELSE 0 END FROM core_system_setting WHERE pkey = 'sso.autoCreateUser' FETCH FIRST 1 ROWS ONLY), auto_create_user),
    require_https = COALESCE((SELECT CASE WHEN LOWER(DBMS_LOB.SUBSTR(pval, 4000, 1)) = 'true' THEN 1 ELSE 0 END FROM core_system_setting WHERE pkey = 'sso.requireHttps' FETCH FIRST 1 ROWS ONLY), require_https),
    update_time = ROUND((CAST(SYSTIMESTAMP AS DATE) - DATE '1970-01-01') * 86400000)
WHERE provider_key = 'default';

INSERT INTO core_iam_org (id, pid, name, code, path, "LEVEL", sort, enable, readonly, create_time, update_time) VALUES (1, 0, '默认组织', 'default', '/1/', 0, 0, 1, 1, 1779664240000, 1779664240000);

INSERT INTO core_iam_role (id, oid, name, code, description, type_code, readonly, system_role, org_admin, create_time, update_time) VALUES (1, 1, '系统管理员', 'system_admin', '拥有全部系统管理和资源管理权限', 1, 1, 1, 1, 1779664240000, 1779664240000);
INSERT INTO core_iam_role (id, oid, name, code, description, type_code, readonly, system_role, org_admin, create_time, update_time) VALUES (2, 1, '普通用户', 'member', '默认业务使用角色', 2, 0, 1, 0, 1779664240000, 1779664240000);
INSERT INTO core_iam_role (id, oid, name, code, description, type_code, readonly, system_role, org_admin, create_time, update_time) VALUES (3, 1, '审计只读', 'auditor', '面向审计和巡检场景的只读角色', 3, 1, 1, 0, 1779664240000, 1779664240000);

INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (73, 15, 2, 'org-management', 'system/org', 5, 'org', '/org-management', 0, 1, 1);
INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (74, 15, 2, 'role-management', 'system/role', 6, 'peoples', '/role-management', 0, 1, 1);
INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (75, 15, 2, 'permission-management', 'system/permission', 7, 'auth', '/permission-management', 0, 1, 1);
INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (76, 15, 2, 'audit-log', 'system/audit-log', 8, 'log', '/audit-log', 0, 1, 1);
INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (77, 4, 2, 'data-asset', 'visualized/data/asset', 3, 'association', '/asset', 0, 1, 1);
INSERT INTO core_iam_menu (id, pid, type, name, component, menu_sort, icon, path, hidden, in_layout, auth) VALUES (78, 4, 2, 'cache-task', 'visualized/data/cache-task', 4, 'task', '/cache-task', 0, 1, 1);

INSERT INTO core_iam_user_org (id, "UID", oid, default_org, create_time)
SELECT id, id, 1, 1, COALESCE(create_time, 1779664240000) FROM core_iam_user;

INSERT INTO core_iam_user_role (id, "UID", oid, rid, create_time)
SELECT 100000 + ROW_NUMBER() OVER (ORDER BY u.id),
       u.id, 1, CASE WHEN u.is_admin = 1 THEN 1 ELSE 2 END, COALESCE(u.create_time, 1779664240000)
FROM core_iam_user u;

INSERT INTO core_iam_role_menu_permission (id, rid, menu_id, permission, create_time)
SELECT id * 100 + 1, 1, id, 'manage', 1779664240000 FROM core_iam_menu WHERE auth = 1 OR id IN (15, 16, 64, 67, 68, 69, 71, 72);

INSERT INTO core_iam_role_menu_permission (id, rid, menu_id, permission, create_time)
SELECT id * 100 + 2, 2, id, 'read', 1779664240000 FROM core_iam_menu WHERE auth = 1 AND id NOT IN (15, 16, 64, 67, 68, 69, 71, 72, 73, 74, 75, 76);

INSERT INTO core_iam_role_menu_permission (id, rid, menu_id, permission, create_time)
SELECT id * 100 + 3, 3, id, 'read', 1779664240000 FROM core_iam_menu WHERE auth = 1 AND id NOT IN (15, 16, 64, 67, 68, 69, 71, 72, 73, 74, 75);

INSERT INTO core_iam_resource_index (id, resource_id, resource_type, oid, creator, name, create_time, update_time)
SELECT 1000000000000000 + ROW_NUMBER() OVER (ORDER BY ds.id), TO_CHAR(ds.id), 'datasource', 1, CAST(ds.create_by AS NUMBER(19,0)), ds.name, ds.create_time, ds.update_time
FROM core_datasource ds
WHERE ds.type <> 'folder';

INSERT INTO core_iam_resource_index (id, resource_id, resource_type, oid, creator, name, create_time, update_time)
SELECT 2000000000000000 + ROW_NUMBER() OVER (ORDER BY dg.id), TO_CHAR(dg.id), 'dataset', 1, CAST(dg.create_by AS NUMBER(19,0)), dg.name, dg.create_time, dg.last_update_time
FROM core_dataset dg
WHERE dg.node_type = 'dataset';

INSERT INTO core_iam_resource_index (id, resource_id, resource_type, oid, creator, name, create_time, update_time)
SELECT 3000000000000000 + ROW_NUMBER() OVER (ORDER BY v.id), TO_CHAR(v.id), CASE WHEN v.type = 'dataV' THEN 'screen' ELSE 'panel' END,
       COALESCE(CAST(NULLIF(v.org_id, '') AS NUMBER(19,0)), 1), CAST(v.create_by AS NUMBER(19,0)), v.name, v.create_time, v.update_time
FROM core_visualization v
WHERE v.delete_flag = 0 AND v.node_type = 'leaf';

UPDATE core_iam_menu
SET icon = CASE id
    WHEN 73 THEN 'org'
    WHEN 74 THEN 'peoples'
    WHEN 75 THEN 'auth'
    ELSE icon
END
WHERE id IN (73, 74, 75);

UPDATE core_iam_role
SET readonly = 0
WHERE id = 2 AND code = 'member';

DELETE FROM core_iam_role_menu_permission WHERE menu_id = 73;

DELETE FROM core_iam_menu WHERE id = 73;


-- ----------------------------------------------------------------------
-- Section 2
-- ----------------------------------------------------------------------
ALTER TABLE core_export_task ADD (
  worker_id VARCHAR2(128) DEFAULT NULL,
  heartbeat_time NUMBER(19,0) DEFAULT NULL,
  retry_count NUMBER(10,0) DEFAULT 0,
  lock_version NUMBER(19,0) DEFAULT 0,
  next_fire_time NUMBER(19,0) DEFAULT NULL,
  last_enqueue_time NUMBER(19,0) DEFAULT NULL,
  last_error CLOB
);


-- ----------------------------------------------------------------------
-- Section 3
-- ----------------------------------------------------------------------
ALTER TABLE core_datasource_sync_task ADD (
  worker_id VARCHAR2(128) DEFAULT NULL,
  heartbeat_time NUMBER(19,0) DEFAULT NULL,
  retry_count NUMBER(10,0) DEFAULT 0,
  lock_version NUMBER(19,0) DEFAULT 0,
  next_fire_time NUMBER(19,0) DEFAULT NULL,
  last_enqueue_time NUMBER(19,0) DEFAULT NULL,
  last_error CLOB
);

CREATE INDEX idx_ds_sync_task_enqueue ON core_datasource_sync_task (task_status, last_enqueue_time);
CREATE INDEX idx_ds_sync_task_heartbeat ON core_datasource_sync_task (task_status, heartbeat_time);


-- ----------------------------------------------------------------------
-- Section 4
-- ----------------------------------------------------------------------
ALTER TABLE core_dataset_sync_task ADD (
  last_enqueue_time NUMBER(19,0) DEFAULT NULL
);

CREATE INDEX idx_dataset_sync_task_enqueue ON core_dataset_sync_task (task_status, last_enqueue_time);


-- ----------------------------------------------------------------------
-- Section 5
-- ----------------------------------------------------------------------
ALTER TABLE core_dataset_sync_task ADD (
  worker_id VARCHAR2(128) DEFAULT NULL,
  retry_count NUMBER(10,0) DEFAULT 0,
  lock_version NUMBER(19,0) DEFAULT 0,
  next_fire_time NUMBER(19,0) DEFAULT NULL,
  last_error CLOB
);

CREATE INDEX idx_dataset_sync_task_hb ON core_dataset_sync_task (task_status, heartbeat_time);


-- ----------------------------------------------------------------------
-- Section 6
-- ----------------------------------------------------------------------
CREATE TABLE core_scheduled_task_queue_state (
  state_id VARCHAR2(64) NOT NULL,
  task_type VARCHAR2(50) NOT NULL,
  task_id VARCHAR2(128) DEFAULT NULL,
  payload_hash VARCHAR2(64) NOT NULL,
  status VARCHAR2(32) NOT NULL,
  worker_id VARCHAR2(128) DEFAULT NULL,
  enqueue_time NUMBER(19,0) NOT NULL,
  start_time NUMBER(19,0) DEFAULT NULL,
  end_time NUMBER(19,0) DEFAULT NULL,
  heartbeat_time NUMBER(19,0) DEFAULT NULL,
  retry_count NUMBER(10,0) DEFAULT 0,
  lock_version NUMBER(19,0) DEFAULT 0,
  last_error CLOB,
  CONSTRAINT pk_scheduled_task_queue_state PRIMARY KEY (state_id)
);

CREATE INDEX idx_sched_task_queue_status ON core_scheduled_task_queue_state (status, heartbeat_time);
CREATE INDEX idx_sched_task_queue_task ON core_scheduled_task_queue_state (task_type, task_id);


-- ----------------------------------------------------------------------
-- Section 7
-- ----------------------------------------------------------------------
CREATE INDEX idx_audit_log_filter_time ON core_audit_log (operation_type, resource_type, operation_time);
