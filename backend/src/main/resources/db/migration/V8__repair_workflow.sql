-- Forward-only additive migration; V7 orders and their history remain unchanged.
ALTER TABLE repair_order
    ADD COLUMN assignee_id BIGINT NULL,
    ADD COLUMN assignee_name VARCHAR(64) NULL,
    ADD COLUMN assigned_by BIGINT NULL,
    ADD COLUMN assigned_by_name VARCHAR(64) NULL,
    ADD COLUMN assigned_at TIMESTAMP(6) NULL,
    ADD COLUMN started_at TIMESTAMP(6) NULL,
    ADD COLUMN completed_at TIMESTAMP(6) NULL,
    ADD COLUMN completion_note VARCHAR(500) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_repair_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_repair_assigner FOREIGN KEY (assigned_by) REFERENCES sys_user(id),
    ADD KEY idx_repair_assignee (assignee_id, status, id);

ALTER TABLE repair_status_log
    ADD COLUMN previous_assignee_id BIGINT NULL,
    ADD COLUMN previous_assignee_name VARCHAR(64) NULL,
    ADD COLUMN assignee_id BIGINT NULL,
    ADD COLUMN assignee_name VARCHAR(64) NULL,
    ADD CONSTRAINT fk_repair_log_previous_assignee FOREIGN KEY (previous_assignee_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_repair_log_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user(id);
