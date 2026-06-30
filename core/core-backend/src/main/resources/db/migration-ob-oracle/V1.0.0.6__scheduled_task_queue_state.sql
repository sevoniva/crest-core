CREATE TABLE scheduled_task_queue_state (
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

CREATE INDEX idx_sched_task_queue_status ON scheduled_task_queue_state (status, heartbeat_time);
CREATE INDEX idx_sched_task_queue_task ON scheduled_task_queue_state (task_type, task_id);
