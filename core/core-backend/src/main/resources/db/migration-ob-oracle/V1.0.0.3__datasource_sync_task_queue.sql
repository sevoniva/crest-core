ALTER TABLE data_source_sync_task ADD (
  worker_id VARCHAR2(128) DEFAULT NULL,
  heartbeat_time NUMBER(19,0) DEFAULT NULL,
  retry_count NUMBER(10,0) DEFAULT 0,
  lock_version NUMBER(19,0) DEFAULT 0,
  next_fire_time NUMBER(19,0) DEFAULT NULL,
  last_enqueue_time NUMBER(19,0) DEFAULT NULL,
  last_error CLOB
);

CREATE INDEX idx_ds_sync_task_enqueue ON data_source_sync_task (task_status, last_enqueue_time);
CREATE INDEX idx_ds_sync_task_heartbeat ON data_source_sync_task (task_status, heartbeat_time);
