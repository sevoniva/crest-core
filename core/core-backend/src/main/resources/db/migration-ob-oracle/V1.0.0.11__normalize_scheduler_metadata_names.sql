-- Crest Core 首版调度元数据命名统一。
-- 执行前请备份系统库；如果旧名称和新名称同时存在，脚本会中断以避免覆盖数据。

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

CREATE OR REPLACE PROCEDURE crest_rename_column_if_needed(
  target_table_name IN VARCHAR2,
  old_column_name IN VARCHAR2,
  new_column_name IN VARCHAR2
) AS
  old_count NUMBER := 0;
  new_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO old_count
  FROM USER_TAB_COLUMNS
  WHERE TABLE_NAME = UPPER(target_table_name) AND COLUMN_NAME = UPPER(old_column_name);

  SELECT COUNT(1) INTO new_count
  FROM USER_TAB_COLUMNS
  WHERE TABLE_NAME = UPPER(target_table_name) AND COLUMN_NAME = UPPER(new_column_name);

  IF old_count = 1 AND new_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ' || target_table_name || ' RENAME COLUMN ' || old_column_name || ' TO ' || new_column_name;
  ELSIF old_count = 1 AND new_count = 1 THEN
    RAISE_APPLICATION_ERROR(-20002, '元数据列改名冲突：旧列和新列同时存在，请先人工确认数据');
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_drop_constraint_if_exists(
  target_table_name IN VARCHAR2,
  target_constraint_name IN VARCHAR2
) AS
  constraint_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO constraint_count
  FROM USER_CONSTRAINTS
  WHERE TABLE_NAME = UPPER(target_table_name) AND CONSTRAINT_NAME = UPPER(target_constraint_name);

  IF constraint_count = 1 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ' || target_table_name || ' DROP CONSTRAINT ' || target_constraint_name;
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_assert_table_rename_safe(
  old_table_name IN VARCHAR2,
  new_table_name IN VARCHAR2
) AS
  old_count NUMBER := 0;
  new_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO old_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(old_table_name);
  SELECT COUNT(1) INTO new_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(new_table_name);

  IF old_count = 1 AND new_count = 1 THEN
    RAISE_APPLICATION_ERROR(-20001, '元数据表改名冲突：旧表和新表同时存在，请先人工确认数据');
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_assert_column_rename_safe(
  target_table_name IN VARCHAR2,
  old_column_name IN VARCHAR2,
  new_column_name IN VARCHAR2
) AS
  old_count NUMBER := 0;
  new_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO old_count
  FROM USER_TAB_COLUMNS
  WHERE TABLE_NAME = UPPER(target_table_name) AND COLUMN_NAME = UPPER(old_column_name);

  SELECT COUNT(1) INTO new_count
  FROM USER_TAB_COLUMNS
  WHERE TABLE_NAME = UPPER(target_table_name) AND COLUMN_NAME = UPPER(new_column_name);

  IF old_count = 1 AND new_count = 1 THEN
    RAISE_APPLICATION_ERROR(-20002, '元数据列改名冲突：旧列和新列同时存在，请先人工确认数据');
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_drop_index_if_exists(target_index_name IN VARCHAR2) AS
  index_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO index_count
  FROM USER_INDEXES
  WHERE INDEX_NAME = UPPER(target_index_name);

  IF index_count = 1 THEN
    EXECUTE IMMEDIATE 'DROP INDEX ' || target_index_name;
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_add_index_if_needed(
  target_table_name IN VARCHAR2,
  target_index_name IN VARCHAR2,
  index_columns IN VARCHAR2
) AS
  table_count NUMBER := 0;
  index_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO table_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(target_table_name);
  SELECT COUNT(1) INTO index_count FROM USER_INDEXES WHERE INDEX_NAME = UPPER(target_index_name);

  IF table_count = 1 AND index_count = 0 THEN
    EXECUTE IMMEDIATE 'CREATE INDEX ' || target_index_name || ' ON ' || target_table_name || ' (' || index_columns || ')';
  END IF;
END;
/

CREATE OR REPLACE PROCEDURE crest_add_foreign_key_if_needed(
  target_table_name IN VARCHAR2,
  target_constraint_name IN VARCHAR2,
  target_columns IN VARCHAR2,
  reference_table_name IN VARCHAR2,
  reference_columns IN VARCHAR2
) AS
  table_count NUMBER := 0;
  reference_table_count NUMBER := 0;
  constraint_count NUMBER := 0;
BEGIN
  SELECT COUNT(1) INTO table_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(target_table_name);
  SELECT COUNT(1) INTO reference_table_count FROM USER_TABLES WHERE TABLE_NAME = UPPER(reference_table_name);
  SELECT COUNT(1) INTO constraint_count
  FROM USER_CONSTRAINTS
  WHERE TABLE_NAME = UPPER(target_table_name) AND CONSTRAINT_NAME = UPPER(target_constraint_name);

  IF table_count = 1 AND reference_table_count = 1 AND constraint_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ' || target_table_name || ' ADD CONSTRAINT ' || target_constraint_name
      || ' FOREIGN KEY (' || target_columns || ') REFERENCES ' || reference_table_name || ' (' || reference_columns || ')';
  END IF;
END;
/

BEGIN
  crest_assert_table_rename_safe('QRTZ_JOB_DETAILS', 'core_schedule_job_details');
  crest_assert_table_rename_safe('QRTZ_TRIGGERS', 'core_schedule_triggers');
  crest_assert_table_rename_safe('QRTZ_BLOB_TRIGGERS', 'core_schedule_blob_triggers');
  crest_assert_table_rename_safe('QRTZ_CALENDARS', 'core_schedule_calendars');
  crest_assert_table_rename_safe('QRTZ_CRON_TRIGGERS', 'core_schedule_cron_triggers');
  crest_assert_table_rename_safe('QRTZ_FIRED_TRIGGERS', 'core_schedule_fired_triggers');
  crest_assert_table_rename_safe('QRTZ_LOCKS', 'core_schedule_locks');
  crest_assert_table_rename_safe('QRTZ_PAUSED_TRIGGER_GRPS', 'core_schedule_paused_trigger_groups');
  crest_assert_table_rename_safe('QRTZ_SCHEDULER_STATE', 'core_schedule_scheduler_state');
  crest_assert_table_rename_safe('QRTZ_SIMPLE_TRIGGERS', 'core_schedule_simple_triggers');
  crest_assert_table_rename_safe('QRTZ_SIMPROP_TRIGGERS', 'core_schedule_simprop_triggers');
  crest_assert_table_rename_safe('core_template_category_rel', 'core_template_category_relation');
  crest_assert_table_rename_safe('core_export_download', 'core_export_download_task');
  crest_assert_column_rename_safe('core_dataset', 'qrtz_instance', 'scheduler_fire_instance_id');
  crest_assert_column_rename_safe('core_datasource', 'qrtz_instance', 'scheduler_fire_instance_id');

  crest_drop_constraint_if_exists('QRTZ_BLOB_TRIGGERS', 'QRTZ_BLOB_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('QRTZ_CRON_TRIGGERS', 'QRTZ_CRON_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('QRTZ_SIMPLE_TRIGGERS', 'QRTZ_SIMPLE_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('QRTZ_SIMPROP_TRIGGERS', 'QRTZ_SIMPROP_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('QRTZ_TRIGGERS', 'QRTZ_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('core_schedule_blob_triggers', 'QRTZ_BLOB_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('core_schedule_cron_triggers', 'QRTZ_CRON_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('core_schedule_simple_triggers', 'QRTZ_SIMPLE_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('core_schedule_simprop_triggers', 'QRTZ_SIMPROP_TRIGGERS_ibfk_1');
  crest_drop_constraint_if_exists('core_schedule_triggers', 'QRTZ_TRIGGERS_ibfk_1');

  crest_drop_index_if_exists('SCHED_NAME');

  crest_rename_table_if_needed('QRTZ_JOB_DETAILS', 'core_schedule_job_details');
  crest_rename_table_if_needed('QRTZ_TRIGGERS', 'core_schedule_triggers');
  crest_rename_table_if_needed('QRTZ_BLOB_TRIGGERS', 'core_schedule_blob_triggers');
  crest_rename_table_if_needed('QRTZ_CALENDARS', 'core_schedule_calendars');
  crest_rename_table_if_needed('QRTZ_CRON_TRIGGERS', 'core_schedule_cron_triggers');
  crest_rename_table_if_needed('QRTZ_FIRED_TRIGGERS', 'core_schedule_fired_triggers');
  crest_rename_table_if_needed('QRTZ_LOCKS', 'core_schedule_locks');
  crest_rename_table_if_needed('QRTZ_PAUSED_TRIGGER_GRPS', 'core_schedule_paused_trigger_groups');
  crest_rename_table_if_needed('QRTZ_SCHEDULER_STATE', 'core_schedule_scheduler_state');
  crest_rename_table_if_needed('QRTZ_SIMPLE_TRIGGERS', 'core_schedule_simple_triggers');
  crest_rename_table_if_needed('QRTZ_SIMPROP_TRIGGERS', 'core_schedule_simprop_triggers');
  crest_rename_table_if_needed('core_template_category_rel', 'core_template_category_relation');
  crest_rename_table_if_needed('core_export_download', 'core_export_download_task');

  crest_rename_column_if_needed('core_dataset', 'qrtz_instance', 'scheduler_fire_instance_id');
  crest_rename_column_if_needed('core_datasource', 'qrtz_instance', 'scheduler_fire_instance_id');

  crest_add_index_if_needed(
    'core_schedule_triggers',
    'idx_csch_trigger_job',
    'SCHED_NAME, JOB_NAME, JOB_GROUP'
  );

  crest_add_foreign_key_if_needed(
    'core_schedule_blob_triggers',
    'fk_csch_blob_trigger',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP',
    'core_schedule_triggers',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP'
  );
  crest_add_foreign_key_if_needed(
    'core_schedule_cron_triggers',
    'fk_csch_cron_trigger',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP',
    'core_schedule_triggers',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP'
  );
  crest_add_foreign_key_if_needed(
    'core_schedule_simple_triggers',
    'fk_csch_simple_trigger',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP',
    'core_schedule_triggers',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP'
  );
  crest_add_foreign_key_if_needed(
    'core_schedule_simprop_triggers',
    'fk_csch_simprop_trigger',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP',
    'core_schedule_triggers',
    'SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP'
  );
  crest_add_foreign_key_if_needed(
    'core_schedule_triggers',
    'fk_csch_trigger_job',
    'SCHED_NAME, JOB_NAME, JOB_GROUP',
    'core_schedule_job_details',
    'SCHED_NAME, JOB_NAME, JOB_GROUP'
  );
END;
/

COMMENT ON COLUMN core_dataset.scheduler_fire_instance_id IS '调度触发实例编号';
COMMENT ON COLUMN core_datasource.scheduler_fire_instance_id IS '调度触发实例编号';

DROP PROCEDURE crest_add_foreign_key_if_needed;
DROP PROCEDURE crest_add_index_if_needed;
DROP PROCEDURE crest_drop_index_if_exists;
DROP PROCEDURE crest_drop_constraint_if_exists;
DROP PROCEDURE crest_assert_column_rename_safe;
DROP PROCEDURE crest_assert_table_rename_safe;
DROP PROCEDURE crest_rename_column_if_needed;
DROP PROCEDURE crest_rename_table_if_needed;
