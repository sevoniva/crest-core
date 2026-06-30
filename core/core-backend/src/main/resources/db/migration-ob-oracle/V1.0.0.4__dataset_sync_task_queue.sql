ALTER TABLE dataset_sync_task ADD (
  last_enqueue_time NUMBER(19,0) DEFAULT NULL
);

CREATE INDEX idx_dataset_sync_task_enqueue ON dataset_sync_task (task_status, last_enqueue_time);
