CREATE TABLE operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    before_data JSON NULL,
    after_data JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_operation_log_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    KEY idx_operation_log_target (target_type, target_id, id),
    KEY idx_operation_log_created (created_at, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE INDEX idx_sys_user_role_enabled ON sys_user(role, enabled, id);
